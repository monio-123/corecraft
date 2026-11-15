package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.mo.corecraft.mapper.KpExamAnswerMapper;
import com.mo.corecraft.mapper.KpExamAttemptMapper;
import com.mo.corecraft.mapper.KpExamPaperMapper;
import com.mo.corecraft.mapper.KpExamQuestionMapper;
import com.mo.corecraft.mapper.KpMasteryEvidenceMapper;
import com.mo.corecraft.mapper.KpTopicLearningMapper;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpExamAnswer;
import com.mo.corecraft.model.entity.KpExamAttempt;
import com.mo.corecraft.model.entity.KpExamPaper;
import com.mo.corecraft.model.entity.KpExamQuestion;
import com.mo.corecraft.model.entity.KpMasteryEvidence;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.entity.KpTopicLearning;
import com.mo.corecraft.service.KpMasteryEvidenceService;
import com.mo.corecraft.service.support.MasteryCalculator;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 证据重建与投影重放（设计 §5 / §6 / §10.6）。
 *
 * <p>证据不可变：重新判分/重新归属 = 新 revision 的新证据 + 旧证据打 {@code superseded_by}。
 * 投影全是缓存：{@link #recomputeTopics} 从证据表确定性重放，幂等、可安全重复跑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpMasteryEvidenceServiceImpl implements KpMasteryEvidenceService {

    /** 整题证据的 rubric key（设计 §10.6：非空防唯一键 NULL 放行） */
    private static final String RUBRIC_QUESTION = "__question__";
    /** 封闭题判分可靠性高、开放题低（设计 §4.5） */
    private static final BigDecimal RELIABILITY_CLOSED = new BigDecimal("1.0");
    private static final BigDecimal RELIABILITY_OPEN = new BigDecimal("0.7");
    private static final Set<String> CLOSED_TYPES = Set.of("single", "multiple", "judge", "fill");

    private final KpMasteryEvidenceMapper evidenceMapper;
    private final KpExamAttemptMapper attemptMapper;
    private final KpExamAnswerMapper answerMapper;
    private final KpExamQuestionMapper questionMapper;
    private final KpExamPaperMapper paperMapper;
    private final KpTopicMapper topicMapper;
    private final KpTopicLearningMapper learningMapper;

    @Override
    @Transactional
    public void regenerateFromAttempt(Long attemptId) {
        KpExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            return;
        }
        List<KpExamAnswer> answers = answerMapper.selectList(Wrappers.<KpExamAnswer>lambdaQuery()
                .eq(KpExamAnswer::getAttemptId, attemptId));
        if (answers.isEmpty()) {
            return;
        }
        List<KpExamQuestion> questions = questionMapper.selectList(Wrappers.<KpExamQuestion>lambdaQuery()
                .in(KpExamQuestion::getId, answers.stream().map(KpExamAnswer::getQuestionId).toList()));
        Map<Long, KpExamQuestion> questionById = new HashMap<>();
        for (KpExamQuestion q : questions) {
            questionById.put(q.getId(), q);
        }

        // 同卷历史作答：算重做新颖性 + 同题同答案的「完全无效」判定（设计 §5.3）
        List<KpExamAttempt> paperAttempts = attemptMapper.selectList(Wrappers.<KpExamAttempt>lambdaQuery()
                .eq(KpExamAttempt::getPaperId, attempt.getPaperId())
                .eq(KpExamAttempt::getUserId, attempt.getUserId())
                .lt(KpExamAttempt::getId, attemptId)
                .orderByAsc(KpExamAttempt::getId));
        int replayIndex = paperAttempts.size();               // 0 = 首次作答
        BigDecimal novelty = BigDecimal.ONE.divide(
                BigDecimal.valueOf(replayIndex + 1), java.math.MathContext.DECIMAL64);
        Map<Long, String> prevAnswers = new HashMap<>();      // questionId → 上次作答原文
        if (!paperAttempts.isEmpty()) {
            List<Long> prevIds = paperAttempts.stream().map(KpExamAttempt::getId).toList();
            for (KpExamAnswer prev : answerMapper.selectList(Wrappers.<KpExamAnswer>lambdaQuery()
                    .in(KpExamAnswer::getAttemptId, prevIds))) {
                prevAnswers.putIfAbsent(prev.getQuestionId(),
                        prev.getAnswerPayload() == null ? "" : prev.getAnswerPayload());
            }
        }

        LocalDateTime assessedAt = attempt.getSubmittedAt() != null
                ? attempt.getSubmittedAt() : attempt.getStartedAt();

        // 证据来源存试卷 kind（quiz/exam/review，设计 §5.1「来源是随堂测验、目录考试还是每日复习」）：
        // 恒存 "paper" 会让可信度的「来源多样性」永远凑不满 2 种（MasteryCalculator 6.4）。
        KpExamPaper paper = paperMapper.selectById(attempt.getPaperId());
        String source = paper == null || paper.getKind() == null || paper.getKind().isBlank()
                ? "exam" : paper.getKind();

        // 旧证据整体留痕（先建新、再打旧的指针；指针匹配不上时置 0 = 无替代行）
        List<KpMasteryEvidence> oldEvidence = evidenceMapper.selectList(Wrappers.<KpMasteryEvidence>lambdaQuery()
                .eq(KpMasteryEvidence::getAttemptId, attemptId)
                .isNull(KpMasteryEvidence::getSupersededBy));

        List<KpMasteryEvidence> created = new ArrayList<>();
        Set<Long> touchedTopics = new LinkedHashSet<>();

        // 涉及的知识点**一次取齐**：归属在 作答×归属 双层循环里逐条 selectById 是 N+1
        // （一卷几十题、每题多个归属时逐条查询被放大）。归属只依赖题目 ⇒ 顺带按题缓存解析。
        Map<Long, List<Attribution>> attributionsByQuestion = new HashMap<>();
        Set<Long> attrTopicIds = new LinkedHashSet<>();
        for (KpExamAnswer answer : answers) {
            KpExamQuestion q = questionById.get(answer.getQuestionId());
            if (q == null) {
                continue;
            }
            List<Attribution> attrs = attributionsByQuestion.computeIfAbsent(q.getId(), k -> parseAttributions(q));
            for (Attribution a : attrs) {
                attrTopicIds.add(a.topicId());
            }
        }
        Map<Long, KpTopic> topicById = attrTopicIds.isEmpty() ? Map.of()
                : topicMapper.selectBatchIds(attrTopicIds).stream()
                        .collect(Collectors.toMap(KpTopic::getId, t -> t, (a, b) -> a));

        for (KpExamAnswer answer : answers) {
            if (answer.getVerdict() == null && answer.getScoreRatio() == null) {
                continue; // 未判定不产生证据，也绝不落 0（设计 §12.4）
            }
            KpExamQuestion question = questionById.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }
            // 同题 + 相同答案重复提交 ⇒ 该题证据完全无效（换答案也刷不动，设计 §5.3）
            String prev = prevAnswers.get(answer.getQuestionId());
            boolean sameAnswer = prev != null
                    && Objects.equals(prev, answer.getAnswerPayload() == null ? "" : answer.getAnswerPayload());

            List<RubricScore> rubricScores = parseRubrics(answer);
            List<Attribution> attributions = attributionsByQuestion.get(question.getId());

            for (Attribution attr : attributions) {
                RubricScore rubric = rubricScores.stream()
                        .filter(r -> r.key().equals(attr.rubricKey()))
                        .findFirst()
                        .orElse(null);
                if (rubric == null || rubric.scoreRatio() == null) {
                    continue;
                }
                KpMasteryEvidence e = new KpMasteryEvidence();
                e.setUserId(attempt.getUserId());
                e.setTopicId(attr.topicId());
                e.setAttemptId(attemptId);
                e.setAnswerId(answer.getId());
                e.setRubricKey(attr.rubricKey());
                e.setGradeRevision(answer.getGradeRevision() == null ? 0 : answer.getGradeRevision());
                e.setAttributionRevision(question.getAttributionRevision() == null ? 0 : question.getAttributionRevision());
                e.setSource(source);
                e.setScoreRatio(rubric.scoreRatio());
                e.setTopicWeight(attr.topicWeight());
                e.setReliabilityWeight(isClosed(question.getType()) ? RELIABILITY_CLOSED : RELIABILITY_OPEN);
                boolean primary = attr.topicWeight() != null
                        && attr.topicWeight().compareTo(BigDecimal.ONE) >= 0;
                if (sameAnswer) {
                    // 完全无效：留原始记录，对 A/R/可信度/H 全部无效
                    e.setNoveltyWeight(BigDecimal.ZERO);
                    e.setAbilityEligible(false);
                    e.setRetentionEligible(false);
                    e.setScheduleEligible(false);
                } else {
                    e.setNoveltyWeight(novelty);
                    e.setAbilityEligible(true);
                    e.setRetentionEligible(primary);
                    e.setScheduleEligible(primary);
                }
                KpTopic topic = topicById.get(attr.topicId());
                if (topic == null || !Objects.equals(topic.getUserId(), attempt.getUserId())) {
                    continue; // 归属必须校验 user_id，不能跨用户挂证据（设计 §10.6）
                }
                e.setContentHash(topic.getContentHash());
                e.setContentEpoch(topic.getContentEpoch() == null ? 1 : topic.getContentEpoch());
                e.setAssessedAt(assessedAt);
                evidenceMapper.insert(e);
                created.add(e);
                touchedTopics.add(attr.topicId());
            }
        }

        // 旧证据打指针：按 (answerId, rubricKey, topicId) 匹配新行；匹配置 0（无替代行）
        for (KpMasteryEvidence old : oldEvidence) {
            long replacement = created.stream()
                    .filter(n -> Objects.equals(n.getAnswerId(), old.getAnswerId())
                            && Objects.equals(n.getRubricKey(), old.getRubricKey())
                            && Objects.equals(n.getTopicId(), old.getTopicId()))
                    .map(KpMasteryEvidence::getId)
                    .findFirst()
                    .orElse(0L);
            KpMasteryEvidence patch = new KpMasteryEvidence();
            patch.setId(old.getId());
            patch.setSupersededBy(replacement);
            // 证据不可变指的是内容；superseded_by 是生命周期指针，走 updateById 只写这一列
            evidenceMapper.updateById(patch);
        }

        if (!touchedTopics.isEmpty()) {
            recomputeTopics(touchedTopics, attempt.getUserId());
        }
    }

    @Override
    @Transactional
    public void recomputeTopics(Set<Long> topicIds, Long userId) {
        for (Long topicId : topicIds) {
            KpTopic topic = topicMapper.selectById(topicId);
            if (topic == null || !Objects.equals(topic.getUserId(), userId)) {
                continue;
            }
            recomputeOne(topic, userId);
        }
    }

    @Override
    @Transactional
    public int recomputeAllTopics(Long userId) {
        if (userId == null) {
            return 0;
        }
        // 重放范围 = 「已有学习投影的」∪「已有证据的」，两者取并集。
        // 不重放"什么都没碰过"的知识点：recomputeOne 会给它新建一行空投影，
        // 白白多出几十行没意义的 kp_topic_learning。
        Set<Long> topicIds = new LinkedHashSet<>();
        for (KpTopicLearning l : learningMapper.selectList(Wrappers.<KpTopicLearning>lambdaQuery()
                .eq(KpTopicLearning::getUserId, userId)
                .select(KpTopicLearning::getTopicId))) {
            topicIds.add(l.getTopicId());
        }
        for (KpMasteryEvidence e : evidenceMapper.selectList(Wrappers.<KpMasteryEvidence>lambdaQuery()
                .eq(KpMasteryEvidence::getUserId, userId)
                .select(KpMasteryEvidence::getTopicId))) {
            topicIds.add(e.getTopicId());
        }
        if (topicIds.isEmpty()) {
            return 0;
        }
        recomputeTopics(topicIds, userId);
        return topicIds.size();
    }

    private void recomputeOne(KpTopic topic, Long userId) {
        KpTopicLearning learning = learningMapper.selectOne(Wrappers.<KpTopicLearning>lambdaQuery()
                .eq(KpTopicLearning::getUserId, userId)
                .eq(KpTopicLearning::getTopicId, topic.getId()));
        int topicEpoch = topic.getContentEpoch() == null ? 1 : topic.getContentEpoch();
        boolean epochSwitched = learning != null && learning.getActiveContentEpoch() != null
                && learning.getActiveContentEpoch() != topicEpoch;

        // 生命周期计数基线：reviewCount = 基线 + epochReviewCount（重放只重建 epoch 计数，设计 §9.2）
        long reviewBaseline;
        long lapseBaseline;
        if (learning == null) {
            reviewBaseline = 0;
            lapseBaseline = 0;
        } else if (epochSwitched) {
            // epoch 切换：到此为止的累计全部冻结为基线
            reviewBaseline = learning.getReviewCount() == null ? 0 : learning.getReviewCount();
            lapseBaseline = learning.getLapseCount() == null ? 0 : learning.getLapseCount();
        } else {
            reviewBaseline = (learning.getReviewCount() == null ? 0 : learning.getReviewCount())
                    - (learning.getEpochReviewCount() == null ? 0 : learning.getEpochReviewCount());
            lapseBaseline = (learning.getLapseCount() == null ? 0 : learning.getLapseCount())
                    - (learning.getEpochLapseCount() == null ? 0 : learning.getEpochLapseCount());
        }

        // 当前 epoch、未被替代的证据；重放顺序 (assessed_at, attempt_id, id)
        List<KpMasteryEvidence> evidence = evidenceMapper.selectList(Wrappers.<KpMasteryEvidence>lambdaQuery()
                .eq(KpMasteryEvidence::getUserId, userId)
                .eq(KpMasteryEvidence::getTopicId, topic.getId())
                .eq(KpMasteryEvidence::getContentEpoch, topicEpoch)
                .isNull(KpMasteryEvidence::getSupersededBy));

        // 可信度要题型：evidence → answer → question.type
        Map<Long, String> typeByAnswer = questionTypesFor(evidence);
        List<MasteryCalculator.EvidenceView> views = new ArrayList<>();
        for (KpMasteryEvidence e : evidence) {
            views.add(new MasteryCalculator.EvidenceView(
                    e.getId(), e.getAttemptId(), e.getContentEpoch(),
                    e.getAssessedAt(), e.getScoreRatio(), e.getTopicWeight(),
                    e.getReliabilityWeight(), e.getNoveltyWeight(),
                    Boolean.TRUE.equals(e.getAbilityEligible()),
                    Boolean.TRUE.equals(e.getRetentionEligible()),
                    Boolean.TRUE.equals(e.getScheduleEligible()),
                    typeByAnswer.get(e.getAnswerId()), e.getSource()));
        }
        List<MasteryCalculator.EvidenceView> sorted = MasteryCalculator.sortForReplay(views);
        LocalDateTime learnedAt = learning == null ? null : learning.getLearnedAt();

        // ⚠️ **「有有效证据 = 已学」**（2026-11-12）：界面上撤掉了独立的「标记为已学」按钮，
        //    用户定的语义是「没有掌握度就是未学，有掌握度就是已学习」⇒
        //    第一次通过检验就等于"学过这条"，learned_at 由证据自动补上。
        //    不这么做的话：撤掉按钮 ⇒ 没人写 learned_at ⇒ 目录的"已学数"永远是 0，
        //    而且调度资格（learnedThen）永远不成立 ⇒ H 不涨、复习永远不排期。
        //
        //    时间取**最早那次有效评估**（不是 now）：学完就等于学过；
        //    拿 now 会把"学完之后才发生的那次考试"错判成"还没学就考了"。
        //    取最早 ⇒ 所有事件都落在 learnedAt 之后 ⇒ 调度资格一次就成立，不用反复重放。
        if (learnedAt == null) {
            learnedAt = firstEventAt(evidence);
        }
        MasteryCalculator.ReplayResult r = MasteryCalculator.replay(sorted, learnedAt);

        boolean isNewRow = learning == null;
        if (isNewRow) {
            learning = new KpTopicLearning();
            learning.setUserId(userId);
            learning.setTopicId(topic.getId());
            learning.setVersion(0L);
        }

        learning.setAbility(r.ability());
        learning.setConfidenceLevel(r.confidenceLevel());
        learning.setLearnedAt(learnedAt);
        learning.setLastMasteryAt(r.lastMasteryAt());
        learning.setLastScheduleAt(r.lastScheduleAt());
        learning.setMemoryStabilityDays(r.memoryStabilityDays());
        learning.setEpochReviewCount(r.epochReviewCount());
        learning.setEpochLapseCount(r.epochLapseCount());
        learning.setReviewCount((int) (reviewBaseline + r.epochReviewCount()));
        learning.setLapseCount((int) (lapseBaseline + r.epochLapseCount()));
        learning.setEffectiveEventWeight(r.effectiveEventWeight());
        learning.setActiveContentEpoch(topicEpoch);
        learning.setAlgorithmVersion(MasteryCalculator.ALGORITHM_VERSION);

        boolean learned = learnedAt != null;
        boolean anyEvent = r.effectiveEventWeight().signum() > 0;
        if (!learned) {
            // 未学 / 取消已学：停用调度，能力历史保留（设计 §3.2）
            learning.setReviewState("inactive");
            learning.setNextReviewAt(null);
        } else if (epochSwitched && !anyEvent) {
            // 内容重大更新且还没重新测过 ⇒ 待复测，立即到期（设计 §3.2 / §7.3）
            learning.setReviewState("pending_reassessment");
            learning.setPendingReason("正文发生重大更新，待复测");
            learning.setNextReviewAt(LocalDateTime.now());
        } else {
            learning.setNextReviewAt(r.nextReviewAt() != null ? r.nextReviewAt() : LocalDateTime.now());
            if (anyEvent && !"active".equals(learning.getReviewState())) {
                // pending_initial / pending_confirm / pending_reassessment 都在首次有效评估后转正
                learning.setReviewState("active");
                learning.setPendingReason(null);
            }
        }
        learning.setVersion((learning.getVersion() == null ? 0 : learning.getVersion()) + 1);
        if (isNewRow) {
            learningMapper.insert(learning);
        } else {
            learningMapper.updateById(learning);
        }
    }

    /**
     * 最早一次**有效评估**的时间（= "第一次学完这条"的时刻）。
     *
     * <p>只有**计入能力**的证据才算（ability_eligible）：被判为无效的那次
     * （同题同答案重复提交）不构成"学过"。
     *
     * @return null = 还没有任何有效证据（没学过）
     */
    private LocalDateTime firstEventAt(List<KpMasteryEvidence> evidence) {
        LocalDateTime min = null;
        for (KpMasteryEvidence e : evidence) {
            if (!Boolean.TRUE.equals(e.getAbilityEligible())) {
                continue;
            }
            if (e.getAssessedAt() == null) {
                continue;
            }
            if (min == null || e.getAssessedAt().isBefore(min)) {
                min = e.getAssessedAt();
            }
        }
        return min;
    }

    private Map<Long, String> questionTypesFor(List<KpMasteryEvidence> evidence) {        Map<Long, String> byAnswer = new HashMap<>();
        if (evidence.isEmpty()) {
            return byAnswer;
        }
        List<Long> answerIds = evidence.stream().map(KpMasteryEvidence::getAnswerId).distinct().toList();
        Map<Long, Long> questionByAnswer = new HashMap<>();
        for (KpExamAnswer a : answerMapper.selectList(Wrappers.<KpExamAnswer>lambdaQuery()
                .select(KpExamAnswer::getId, KpExamAnswer::getQuestionId)
                .in(KpExamAnswer::getId, answerIds))) {
            questionByAnswer.put(a.getId(), a.getQuestionId());
        }
        Map<Long, String> typeByQuestion = new HashMap<>();
        for (KpExamQuestion q : questionMapper.selectList(Wrappers.<KpExamQuestion>lambdaQuery()
                .select(KpExamQuestion::getId, KpExamQuestion::getType)
                .in(KpExamQuestion::getId, questionByAnswer.values().stream().distinct().toList()))) {
            typeByQuestion.put(q.getId(), q.getType());
        }
        questionByAnswer.forEach((answerId, questionId) ->
                byAnswer.put(answerId, typeByQuestion.get(questionId)));
        return byAnswer;
    }

    // ===== JSON 契约：gradePayload = {"rubrics":[{"key","scoreRatio"}]}；attributionPayload = {"items":[{"rubricKey","topicId","topicWeight"}]} =====

    private record RubricScore(String key, BigDecimal scoreRatio) {
    }

    private record Attribution(String rubricKey, Long topicId, BigDecimal topicWeight) {
    }

    private List<RubricScore> parseRubrics(KpExamAnswer answer) {
        List<RubricScore> rubrics = new ArrayList<>();
        JsonNode root = answer.getGradePayload() == null ? null : JsonUtils.readTree(answer.getGradePayload());
        if (root != null && root.has("rubrics") && root.get("rubrics").isArray()) {
            for (JsonNode node : root.get("rubrics")) {
                String key = JsonUtils.text(node, "key");
                BigDecimal ratio = decimal(node.get("scoreRatio"));
                rubrics.add(new RubricScore(key == null || key.isBlank() ? RUBRIC_QUESTION : key, ratio));
            }
        }
        if (rubrics.isEmpty()) {
            // 没有评分点级判定 ⇒ 整题一条（__question__）
            rubrics.add(new RubricScore(RUBRIC_QUESTION, answer.getScoreRatio()));
        }
        return rubrics;
    }

    private List<Attribution> parseAttributions(KpExamQuestion question) {
        List<Attribution> items = new ArrayList<>();
        JsonNode root = question.getAttributionPayload() == null
                ? null : JsonUtils.readTree(question.getAttributionPayload());
        if (root != null && root.has("items") && root.get("items").isArray()) {
            for (JsonNode node : root.get("items")) {
                Long topicId = longOf(node.get("topicId"));
                if (topicId == null) {
                    continue;
                }
                String rubricKey = JsonUtils.text(node, "rubricKey");
                items.add(new Attribution(
                        rubricKey == null || rubricKey.isBlank() ? RUBRIC_QUESTION : rubricKey,
                        topicId, decimal(node.get("topicWeight"))));
            }
        }
        return items;
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() ? null : node.decimalValue();
    }

    private static Long longOf(JsonNode node) {
        return node == null || node.isNull() ? null : node.longValue();
    }

    /**
     * 封闭题判分可靠性高、开放题低（设计 §4.5）。
     * 判定走 {@link MasteryCalculator#normalizeType} 归一化——前端存的键是
     * blank/concept/interview 这套（EXAM_TYPES），直接拿原始串配 CLOSED_TYPES
     * 会把填空当开放题打折，且不报错。
     */
    private static boolean isClosed(String type) {
        return type != null && CLOSED_TYPES.contains(MasteryCalculator.normalizeType(type));
    }
}
