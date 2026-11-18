package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.mapper.KpExamAnswerMapper;
import com.mo.corecraft.mapper.KpExamAttemptMapper;
import com.mo.corecraft.mapper.KpExamPaperMapper;
import com.mo.corecraft.mapper.KpExamQuestionMapper;
import com.mo.corecraft.model.entity.KpExamAnswer;
import com.mo.corecraft.model.entity.KpExamAttempt;
import com.mo.corecraft.model.entity.KpExamPaper;
import com.mo.corecraft.model.entity.KpExamQuestion;
import com.mo.corecraft.model.req.KpAttributionSaveReq;
import com.mo.corecraft.model.req.KpGradeResultReq;
import com.mo.corecraft.model.resp.KpAttemptReviewResp;
import com.mo.corecraft.service.KpExamAnswerService;
import com.mo.corecraft.service.KpExamAttemptService;
import com.mo.corecraft.service.KpMasteryEvidenceService;
import com.mo.corecraft.utils.AppClock;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 判分结果与归属（设计 §11.2 / §4.2）。
 *
 * <p>幂等口径：{@code resultId} 已落库 ⇒ 直接返回，重试不重复产证据；
 * 新 {@code gradeRevision} 走替代流程（旧证据留痕、新证据生效、当前 epoch 重放）。
 * 成绩、证据、学习投影、复习调度在同一事务提交。
 */
@Service
@RequiredArgsConstructor
public class KpExamAnswerServiceImpl implements KpExamAnswerService {

    private final KpExamAnswerMapper answerMapper;
    private final KpExamAttemptMapper attemptMapper;
    private final KpExamQuestionMapper questionMapper;
    private final KpExamPaperMapper paperMapper;
    private final KpExamAttemptService kpExamAttemptService;
    private final KpMasteryEvidenceService masteryEvidenceService;

    @Override
    @Transactional
    public void gradeResult(Long attemptId, KpGradeResultReq req, Long userId) {
        KpExamAttempt attempt = kpExamAttemptService.ownedAttempt(attemptId, userId);
        // 相同 resultId 的回调幂等返回（设计 §10.5）
        boolean duplicate = answerMapper.exists(Wrappers.<KpExamAnswer>lambdaQuery()
                .eq(KpExamAnswer::getAttemptId, attemptId)
                .eq(KpExamAnswer::getResultId, req.getResultId()));
        if (duplicate) {
            return;
        }

        List<KpExamAnswer> answers = answerMapper.selectList(Wrappers.<KpExamAnswer>lambdaQuery()
                .eq(KpExamAnswer::getAttemptId, attemptId));
        Map<Long, KpExamAnswer> byQuestion = new LinkedHashMap<>();
        for (KpExamAnswer a : answers) {
            byQuestion.put(a.getQuestionId(), a);
        }

        for (KpGradeResultReq.Item item : req.getAnswers()) {
            KpExamAnswer answer = byQuestion.get(item.getQuestionId());
            if (answer == null) {
                // 判分回调里出现没作答过的题 ⇒ 新建空作答行（不丢讲解）
                answer = new KpExamAnswer();
                answer.setAttemptId(attemptId);
                answer.setQuestionId(item.getQuestionId());
                answer.setGradeRevision(0);
                answerMapper.insert(answer);
                byQuestion.put(item.getQuestionId(), answer);
            }
            answer.setVerdict(item.getVerdict());
            answer.setScoreRatio(item.getScoreRatio());
            answer.setScore(item.getScore());
            answer.setExplanation(item.getExplanation());
            // gradePayload 按 revision 留痕：旧快照进 history，本次铺在顶层（rubrics 键保持顶层）
            Map<String, Object> payload = item.getGradePayload() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(item.getGradePayload());
            Map<String, Object> merged = new LinkedHashMap<>(payload);
            merged.put("revision", req.getGradeRevision());
            appendRevisionHistory(answer.getGradePayload(), merged);
            answer.setGradePayload(JsonUtils.toJsonString(merged));
            answer.setGradeRevision(req.getGradeRevision());
            answer.setResultId(req.getResultId());
            answer.setGradedAt(AppClock.now());
            answerMapper.updateById(answer);
        }

        // 成绩：partial 时只算已判定的题，未判定不计入也不落 0（设计 §10.4）
        List<KpExamQuestion> questions = questionMapper.selectList(Wrappers.<KpExamQuestion>lambdaQuery()
                .eq(KpExamQuestion::getPaperId, attempt.getPaperId()));
        Map<Long, KpExamQuestion> questionById = new LinkedHashMap<>();
        for (KpExamQuestion q : questions) {
            questionById.put(q.getId(), q);
        }
        BigDecimal total = BigDecimal.ZERO;
        int ungraded = 0;
        for (KpExamAnswer a : byQuestion.values()) {
            if (a.getScoreRatio() == null && a.getVerdict() == null) {
                ungraded++;
                continue;
            }
            KpExamQuestion q = questionById.get(a.getQuestionId());
            BigDecimal max = q == null || q.getMaxScore() == null ? BigDecimal.ZERO : q.getMaxScore();
            BigDecimal ratio = a.getScoreRatio() == null ? BigDecimal.ZERO : a.getScoreRatio();
            total = total.add(max.multiply(ratio));
        }

        KpExamAttempt patch = new KpExamAttempt();
        patch.setId(attemptId);
        patch.setScore(total);
        patch.setStatus(ungraded > 0 ? "partial" : "completed");
        patch.setCompletedAt(AppClock.now());
        attemptMapper.updateById(patch);

        // 证据 + 投影重放：同一事务（设计 §11.2）
        masteryEvidenceService.regenerateFromAttempt(attemptId);
    }

    @Override
    public KpAttemptReviewResp selectReview(Long attemptId, Long userId) {
        KpExamAttempt attempt = kpExamAttemptService.ownedAttempt(attemptId, userId);
        KpExamPaper paper = paperMapper.selectById(attempt.getPaperId());
        KpAttemptReviewResp resp = new KpAttemptReviewResp();
        resp.setAttemptId(attempt.getId());
        resp.setPaperId(attempt.getPaperId());
        resp.setPaperTitle(paper == null ? "" : paper.getTitle());
        resp.setStatus(attempt.getStatus());
        resp.setScore(attempt.getScore());
        resp.setMaxScore(attempt.getMaxScore());

        List<KpExamQuestion> questions = questionMapper.selectList(Wrappers.<KpExamQuestion>lambdaQuery()
                .eq(KpExamQuestion::getPaperId, attempt.getPaperId())
                .orderByAsc(KpExamQuestion::getQuestionNo)
                .orderByAsc(KpExamQuestion::getId));
        Map<Long, KpExamAnswer> answerByQuestion = new LinkedHashMap<>();
        for (KpExamAnswer a : answerMapper.selectList(Wrappers.<KpExamAnswer>lambdaQuery()
                .eq(KpExamAnswer::getAttemptId, attemptId))) {
            answerByQuestion.put(a.getQuestionId(), a);
        }

        List<KpAttemptReviewResp.Item> items = new ArrayList<>();
        for (KpExamQuestion q : questions) {
            KpAttemptReviewResp.Item item = new KpAttemptReviewResp.Item();
            item.setQuestionId(q.getId());
            item.setQuestionNo(q.getQuestionNo());
            item.setType(q.getType());
            item.setMaxScore(q.getMaxScore());
            item.setQuestionPayload(JsonUtils.toStringMap(q.getQuestionPayload()));
            KpExamAnswer a = answerByQuestion.get(q.getId());
            if (a != null) {
                item.setAnswerPayload(JsonUtils.toStringMap(a.getAnswerPayload()));
                item.setVerdict(a.getVerdict());
                item.setScoreRatio(a.getScoreRatio());
                item.setScore(a.getScore());
                item.setExplanation(a.getExplanation());
                item.setGradePayload(JsonUtils.toStringMap(a.getGradePayload()));
            }
            items.add(item);
        }
        resp.setItems(items);
        return resp;
    }

    @Override
    @Transactional
    public void saveAttribution(Long questionId, KpAttributionSaveReq req, Long userId) {
        KpExamQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new CoreCraftException(ResultCodeEnum.NOT_FOUND, "题目不存在");
        }
        KpExamPaper paper = paperMapper.selectById(question.getPaperId());
        if (paper == null || !Objects.equals(paper.getUserId(), userId)) {
            throw new CoreCraftException(ResultCodeEnum.NOT_FOUND, "题目不存在");
        }

        int revision = (question.getAttributionRevision() == null ? 0 : question.getAttributionRevision()) + 1;
        // payload 保留各 revision 快照：旧快照进 history，本次铺在顶层（items 键保持顶层）
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("revision", revision);
        merged.put("status", req.getStatus());
        if (req.getNote() != null) {
            merged.put("note", req.getNote());
        }
        merged.put("items", req.getItems() == null ? List.of() : req.getItems());
        appendRevisionHistory(question.getAttributionPayload(), merged);

        KpExamQuestion patch = new KpExamQuestion();
        patch.setId(questionId);
        patch.setAttributionPayload(JsonUtils.toJsonString(merged));
        patch.setAttributionStatus(req.getStatus());
        patch.setAttributionRevision(revision);
        questionMapper.updateById(patch);

        // 归属变了 ⇒ 该题所在作答的证据按新归属重建（旧证据留痕）
        for (KpExamAnswer a : answerMapper.selectList(Wrappers.<KpExamAnswer>lambdaQuery()
                .eq(KpExamAnswer::getQuestionId, questionId))) {
            masteryEvidenceService.regenerateFromAttempt(a.getAttemptId());
        }
    }

    /** 把上一版 payload 归档进新 payload 的 {@code history} 列表（判分/归因两条 revision 留痕共用）。 */
    private static void appendRevisionHistory(String previousJson, Map<String, Object> merged) {
        if (previousJson == null || previousJson.isBlank()) {
            return;
        }
        Object old = JsonUtils.toJava(JsonUtils.readTree(previousJson));
        List<Object> history = new ArrayList<>();
        if (old instanceof Map<?, ?> oldMap && oldMap.get("history") instanceof List<?> list) {
            history.addAll(list);
        }
        history.add(old);
        merged.put("history", history);
    }
}
