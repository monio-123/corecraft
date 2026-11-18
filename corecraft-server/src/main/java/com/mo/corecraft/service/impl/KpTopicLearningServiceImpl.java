package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.mapper.KpCategoryMapper;
import com.mo.corecraft.mapper.KpMasteryEvidenceMapper;
import com.mo.corecraft.mapper.KpTopicLearningMapper;
import com.mo.corecraft.mapper.KpTopicMapper;
import com.mo.corecraft.model.entity.KpCategory;
import com.mo.corecraft.model.entity.KpMasteryEvidence;
import com.mo.corecraft.model.entity.KpTopic;
import com.mo.corecraft.model.entity.KpTopicLearning;
import com.mo.corecraft.model.req.KpLearningMarkReq;
import com.mo.corecraft.model.resp.KpCategoryLearningResp;
import com.mo.corecraft.model.resp.KpEvidenceResp;
import com.mo.corecraft.model.resp.KpLearningResp;
import com.mo.corecraft.model.resp.KpTopicIndexResp;
import com.mo.corecraft.model.resp.KpTopicLevelResp;
import com.mo.corecraft.service.KpTopicLearningService;
import com.mo.corecraft.service.support.CategoryTreeHelper;
import com.mo.corecraft.service.support.MasteryCalculator;
import com.mo.corecraft.service.support.MasteryLevelResolver;
import com.mo.corecraft.service.support.MasteryScope;
import com.mo.corecraft.utils.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习状态（设计 §3）：学过是显式标记，不自动猜；掌握度是投影，权威在证据表。
 */
@Service
@RequiredArgsConstructor
public class KpTopicLearningServiceImpl implements KpTopicLearningService {

    private final KpTopicLearningMapper learningMapper;
    private final KpTopicMapper topicMapper;
    private final KpMasteryEvidenceMapper evidenceMapper;
    private final KpCategoryMapper categoryMapper;

    @Override
    @Transactional
    public void markLearning(Long topicId, KpLearningMarkReq req, Long userId) {
        KpTopic topic = topicMapper.selectById(topicId);
        if (topic == null || !Objects.equals(topic.getUserId(), userId)) {
            throw new CoreCraftException(ResultCodeEnum.NOT_FOUND, "知识点不存在");
        }
        KpTopicLearning learning = learningMapper.selectOne(Wrappers.<KpTopicLearning>lambdaQuery()
                .eq(KpTopicLearning::getUserId, userId)
                .eq(KpTopicLearning::getTopicId, topicId));
        boolean existed = learning != null;
        boolean hasEvidence = evidenceMapper.exists(Wrappers.<KpMasteryEvidence>lambdaQuery()
                .eq(KpMasteryEvidence::getUserId, userId)
                .eq(KpMasteryEvidence::getTopicId, topicId));
        LocalDateTime now = AppClock.now();

        if (Boolean.TRUE.equals(req.getLearned())) {
            // 空正文不能标记已学（设计 §3.1）
            if (topic.getContent() == null || topic.getContent().isBlank()) {
                throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "空正文不能标记已学");
            }
            if (learning == null) {
                learning = new KpTopicLearning();
                learning.setUserId(userId);
                learning.setTopicId(topicId);
                learning.setVersion(0L);
                learning.setMemoryStabilityDays(BigDecimal.ONE);
                learning.setConfidenceLevel(0);
            }
            learning.setLearnedAt(now);
            // 首次标记且无证据 ⇒ 待首次评估；先考试后标记 / 再次标记 ⇒ 待确认当前状态（设计 §3.2）
            boolean firstMarkNoEvidence = !existed && !hasEvidence;
            learning.setReviewState(firstMarkNoEvidence ? "pending_initial" : "pending_confirm");
            learning.setPendingReason(firstMarkNoEvidence ? "待首次评估" : "待确认当前状态");
            // 两种待办状态到期时间都是当前时间，不沿用旧复习日期
            learning.setNextReviewAt(now);
        } else {
            // 取消已学：停用调度，保留能力、证据和全部考试历史（设计 §3.2）
            if (learning == null) {
                return;
            }
            learning.setLearnedAt(null);
            learning.setReviewState("inactive");
            learning.setPendingReason(req.getReason());
            learning.setNextReviewAt(null);
        }
        learning.setVersion((learning.getVersion() == null ? 0 : learning.getVersion()) + 1);
        if (existed) {
            learningMapper.updateById(learning);
        } else {
            learningMapper.insert(learning);
        }
    }

    @Override
    public KpLearningResp selectLearning(Long topicId, Long userId) {
        KpTopicLearning learning = learningMapper.selectOne(Wrappers.<KpTopicLearning>lambdaQuery()
                .eq(KpTopicLearning::getUserId, userId)
                .eq(KpTopicLearning::getTopicId, topicId));
        KpLearningResp resp = new KpLearningResp();
        resp.setTopicId(topicId);
        if (learning == null) {
            // 没行 = 没学过也没被考过：空投影，前端按「未评估」渲染
            resp.setReviewState("inactive");
            resp.setConfidenceLevel(0);
            resp.setMasteryLevel(MasteryCalculator.masteryLevel(resp.getReviewState(), null));
            return resp;
        }
        resp.setLearnedAt(learning.getLearnedAt());
        resp.setReviewState(learning.getReviewState());
        resp.setPendingReason(learning.getPendingReason());
        resp.setAbility(learning.getAbility());
        resp.setConfidenceLevel(learning.getConfidenceLevel());
        resp.setMemoryStabilityDays(learning.getMemoryStabilityDays());
        resp.setLastMasteryAt(learning.getLastMasteryAt());
        resp.setNextReviewAt(learning.getNextReviewAt());
        resp.setReviewCount(learning.getReviewCount());
        resp.setLapseCount(learning.getLapseCount());
        resp.setActiveContentEpoch(learning.getActiveContentEpoch());
        resp.setAlgorithmVersion(learning.getAlgorithmVersion());
        // 记忆新鲜度 R：对外展示的第二维度（设计 §6.2）
        resp.setFreshness(MasteryCalculator.retentionR(
                learning.getMemoryStabilityDays(), learning.getLastMasteryAt(), AppClock.now()));
        // 展示档位：阈值只在 MasteryCalculator 一处（设计 §6.5，2026-11-14 四档纯分数），
        // 判据 = 当前掌握度 M（ability × 衰减后 R）——**条画的就是它，档位与条永不倒挂**
        resp.setMasteryLevel(MasteryCalculator.masteryLevel(
                resp.getReviewState(),
                MasteryCalculator.masteryM(resp.getAbility(), resp.getFreshness())));
        return resp;
    }

    @Override
    public List<KpEvidenceResp> selectEvidence(Long topicId, Long userId) {
        // 被替代的也返回（留痕可见），重放排除逻辑只看 supersededBy（设计 §10.6）
        return evidenceMapper.selectList(Wrappers.<KpMasteryEvidence>lambdaQuery()
                        .eq(KpMasteryEvidence::getUserId, userId)
                        .eq(KpMasteryEvidence::getTopicId, topicId)
                        .orderByAsc(KpMasteryEvidence::getAssessedAt)
                        .orderByAsc(KpMasteryEvidence::getAttemptId)
                        .orderByAsc(KpMasteryEvidence::getId)).stream()
                .map(e -> {
                    KpEvidenceResp r = new KpEvidenceResp();
                    r.setId(e.getId());
                    r.setAttemptId(e.getAttemptId());
                    r.setAnswerId(e.getAnswerId());
                    r.setRubricKey(e.getRubricKey());
                    r.setSource(e.getSource());
                    r.setScoreRatio(e.getScoreRatio());
                    r.setTopicWeight(e.getTopicWeight());
                    r.setReliabilityWeight(e.getReliabilityWeight());
                    r.setNoveltyWeight(e.getNoveltyWeight());
                    r.setAbilityEligible(e.getAbilityEligible());
                    r.setRetentionEligible(e.getRetentionEligible());
                    r.setScheduleEligible(e.getScheduleEligible());
                    r.setContentEpoch(e.getContentEpoch());
                    r.setAssessedAt(e.getAssessedAt());
                    r.setSupersededBy(e.getSupersededBy());
                    r.setGradeRevision(e.getGradeRevision());
                    r.setAttributionRevision(e.getAttributionRevision());
                    return r;
                })
                .toList();
    }

    @Override
    public List<KpCategoryLearningResp> selectTree(Long rootCategoryId, Long userId) {
        LocalDateTime now = AppClock.now();
        // 三个数据源都是轻量的：结构索引（不碰 content 列，见 KpTopicMapper.xml）、
        // 本人的学习行、目录表。一次捞全在内存里折叠——目录是几十级别，逐层查库才是真浪费。
        List<KpTopicIndexResp> topics = topicMapper.selectIndex(userId, null, null);
        Map<Long, KpTopicLearning> learningByTopic = learningMapper
                .selectList(Wrappers.<KpTopicLearning>lambdaQuery().eq(KpTopicLearning::getUserId, userId))
                .stream().collect(Collectors.toMap(KpTopicLearning::getTopicId, e -> e, (a, b) -> a));
        List<KpCategory> categories = categoryMapper
                .selectList(Wrappers.<KpCategory>lambdaQuery().eq(KpCategory::getUserId, userId));

        // 直挂知识点先归到直接目录；Σ(M×w) / Σw 单独累加（拿不到合格项时 mastery 要是 null 不是 0）
        Map<Long, KpCategoryLearningResp> byId = new HashMap<>();
        Map<Long, BigDecimal> mSum = new HashMap<>();
        Map<Long, BigDecimal> wSum = new HashMap<>();
        // 档位分布（方案 C 顶部概览）：只统计**参与**且**已检验**的条目
        Map<Long, Map<String, Integer>> levelCounts = new HashMap<>();
        for (KpCategory c : categories) {
            KpCategoryLearningResp row = new KpCategoryLearningResp();
            row.setCategoryId(c.getId());
            row.setEligibleCount(0);
            row.setLearnedCount(0);
            row.setAssessedCount(0);
            byId.put(c.getId(), row);
        }
        for (KpTopicIndexResp t : topics) {
            // 未归类不参与任何目录聚合（设计 §3.1）；空正文不是「可学」（§8.1 eligible）
            if (t.getCategoryId() == null || !Boolean.TRUE.equals(t.getHasContent())) {
                continue;
            }
            KpCategoryLearningResp row = byId.get(t.getCategoryId());
            if (row == null) {
                continue; // 挂在已删目录上的脏数据：不进任何聚合
            }
            // ⚠️ **单条显式关闭的条目不进学习聚合**（"这条不算学习材料"）。
            //    判据只有一处（MasteryScope），前端不自己判一遍。
            //    ⚠️ **分母（eligible）不能跟着"参与"走**（2026-11-16 D10）：
            //    「已检验 N / M」的 M 是"书里有多少条可学的"，不是"有多少条被检验过"——
            //    若把 M 收窄成"已检验的"，那个比值恒等于 100%，界面上的信息就没了。
            if (!MasteryScope.eligible(t.getMasteryOverride())) {
                continue;
            }
            row.setEligibleCount(row.getEligibleCount() + 1);
            KpTopicLearning l = learningByTopic.get(t.getId());
            if (l == null || l.getLearnedAt() == null) {
                continue;
            }
            row.setLearnedCount(row.getLearnedCount() + 1);
            // 「有当前 epoch 有效证据」的投影判据：能力 A 非空（重放只给有合格事件的条目算 A）
            if (l.getAbility() == null) {
                continue;
            }
            row.setAssessedCount(row.getAssessedCount() + 1);
            BigDecimal m = MasteryCalculator.masteryM(l.getAbility(),
                    MasteryCalculator.retentionR(l.getMemoryStabilityDays(), l.getLastMasteryAt(), now));
            BigDecimal w = MasteryCalculator.topicWeight(l.getConfidenceLevel() == null ? 0 : l.getConfidenceLevel());
            mSum.merge(t.getCategoryId(), m.multiply(w), BigDecimal::add);
            wSum.merge(t.getCategoryId(), w, BigDecimal::add);
            // 档位分布：只收**已检验**条目的档位（未检验的由「已检验 N / M」那个分母表达）
            String level = MasteryLevelResolver.levelOf(l, now);
            if (level != null) {
                levelCounts.computeIfAbsent(t.getCategoryId(), k -> new HashMap<>())
                        .merge(level, 1, Integer::sum);
            }
        }

        // 子树折叠：每目录的指标 = 直挂 + 全部子目录（递归统计整个子树、同一知识点只算一次，§8.1）
        Map<Long, List<Long>> childrenOf = CategoryTreeHelper.childrenByParent(categories);
        Set<Long> folded = new HashSet<>();
        for (Long id : byId.keySet()) {
            foldUp(id, childrenOf, byId, mSum, wSum, levelCounts, folded);
        }
        for (KpCategoryLearningResp row : byId.values()) {
            row.setMastery(MasteryCalculator.weightedMastery(
                    mSum.get(row.getCategoryId()), wSum.get(row.getCategoryId())));
            Map<String, Integer> counts = levelCounts.get(row.getCategoryId());
            // 没有已检验条目 ⇒ 分布为空（前端据此不画概览条），不是"所有档位 0"
            row.setLevelCounts(counts == null || counts.isEmpty() ? null : counts);
        }

        // 指定根就只回那棵子树；不指定回整棵树（设计 §11.1「批量返回整棵树的聚合」）
        Set<Long> scope = rootCategoryId == null ? null : CategoryTreeHelper.subtreeIdSet(childrenOf, rootCategoryId);
        return byId.values().stream()
                .filter(r -> scope == null || scope.contains(r.getCategoryId()))
                .sorted(Comparator.comparing(KpCategoryLearningResp::getCategoryId))
                .toList();
    }

    @Override
    public List<KpTopicLevelResp> selectMasteryLevels(Long userId) {
        LocalDateTime now = AppClock.now();
        // 只回有效知识点：软删的 / 单条显式关掉掌握度（masteryOverride=false）的不染色，与 selectTree 口径一致
        Map<Long, KpTopic> eligibleTopics = topicMapper.selectList(Wrappers.<KpTopic>lambdaQuery()
                        .select(KpTopic::getId, KpTopic::getMasteryOverride)
                        .eq(KpTopic::getUserId, userId))
                .stream()
                .filter(t -> MasteryScope.eligible(t.getMasteryOverride()))
                .collect(Collectors.toMap(KpTopic::getId, t -> t, (a, b) -> a));
        if (eligibleTopics.isEmpty()) {
            return List.of();
        }
        return learningMapper.selectList(Wrappers.<KpTopicLearning>lambdaQuery()
                        .eq(KpTopicLearning::getUserId, userId)).stream()
                .filter(l -> eligibleTopics.containsKey(l.getTopicId()))
                .map(l -> {
                    String level = MasteryLevelResolver.levelOf(l, now);
                    if (level == null) {
                        return null; // 已学未评估 / 无证据：树行保持原样，不下发
                    }
                    KpTopicLevelResp row = new KpTopicLevelResp();
                    row.setTopicId(l.getTopicId());
                    row.setLevel(level);
                    row.setMastery(MasteryCalculator.masteryM(l.getAbility(),
                            MasteryCalculator.retentionR(l.getMemoryStabilityDays(), l.getLastMasteryAt(), now)));
                    return row;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 后序折叠：先把 child 的子树折进 child，再把 child 并进 id。
     * {@code folded} 收住两个坑：顶层循环重复折同一棵子树；库里历史脏数据的父子成环。
     */
    private void foldUp(Long id, Map<Long, List<Long>> childrenOf, Map<Long, KpCategoryLearningResp> byId,
                        Map<Long, BigDecimal> mSum, Map<Long, BigDecimal> wSum,
                        Map<Long, Map<String, Integer>> levelCounts, Set<Long> folded) {
        if (!folded.add(id)) {
            return;
        }
        for (Long child : childrenOf.getOrDefault(id, List.of())) {
            if (folded.contains(child)) {
                continue; // 已折过或成环：不能再并一次（同一知识点只算一次）
            }
            foldUp(child, childrenOf, byId, mSum, wSum, levelCounts, folded);
            KpCategoryLearningResp c = byId.get(child);
            KpCategoryLearningResp p = byId.get(id);
            if (c == null || p == null) {
                continue;
            }
            p.setEligibleCount(p.getEligibleCount() + c.getEligibleCount());
            p.setLearnedCount(p.getLearnedCount() + c.getLearnedCount());
            p.setAssessedCount(p.getAssessedCount() + c.getAssessedCount());
            mSum.merge(id, mSum.getOrDefault(child, BigDecimal.ZERO), BigDecimal::add);
            wSum.merge(id, wSum.getOrDefault(child, BigDecimal.ZERO), BigDecimal::add);
            // 档位分布一起折（同一知识点只算一次，与其它指标同一个口径）
            Map<String, Integer> childCounts = levelCounts.get(child);
            if (childCounts != null && !childCounts.isEmpty()) {
                Map<String, Integer> acc = levelCounts.computeIfAbsent(id, k -> new HashMap<>());
                childCounts.forEach((k, v) -> acc.merge(k, v, Integer::sum));
            }
        }
    }
}
