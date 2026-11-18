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
import com.mo.corecraft.model.req.KpAnswerSaveReq;
import com.mo.corecraft.model.req.KpAttemptCreateReq;
import com.mo.corecraft.model.resp.KpAttemptResp;
import com.mo.corecraft.service.KpExamAttemptService;
import com.mo.corecraft.utils.AppClock;
import com.mo.corecraft.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KpExamAttemptServiceImpl implements KpExamAttemptService {

    private final KpExamAttemptMapper attemptMapper;
    private final KpExamPaperMapper paperMapper;
    private final KpExamQuestionMapper questionMapper;
    private final KpExamAnswerMapper answerMapper;

    @Override
    @Transactional
    public Long createAttempt(KpAttemptCreateReq req, Long userId) {
        KpExamPaper paper = paperMapper.selectById(req.getPaperId());
        if (paper == null || !Objects.equals(paper.getUserId(), userId)) {
            throw new CoreCraftException(ResultCodeEnum.NOT_FOUND, "试卷不存在");
        }
        if (!"ready".equals(paper.getStatus())) {
            throw new CoreCraftException(ResultCodeEnum.CONFLICT, "试卷还不能作答");
        }
        if (req.getReplayOfAttemptId() != null) {
            KpExamAttempt original = attemptMapper.selectById(req.getReplayOfAttemptId());
            // 重做必须指向「同一张卷、同一个人」的原记录；原记录不动（设计 §10.2.1）
            if (original == null || !Objects.equals(original.getUserId(), userId)
                    || !Objects.equals(original.getPaperId(), paper.getId())) {
                throw new CoreCraftException(ResultCodeEnum.BAD_REQUEST, "原作答记录不存在");
            }
        }
        List<KpExamQuestion> questions = questionMapper.selectList(Wrappers.<KpExamQuestion>lambdaQuery()
                .eq(KpExamQuestion::getPaperId, paper.getId()));
        BigDecimal maxScore = questions.stream()
                .map(KpExamQuestion::getMaxScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        KpExamAttempt attempt = new KpExamAttempt();
        attempt.setPaperId(paper.getId());
        attempt.setUserId(userId);
        attempt.setReplayOfAttemptId(req.getReplayOfAttemptId());
        attempt.setStatus("answering");
        attempt.setStartedAt(AppClock.now());
        attempt.setMaxScore(maxScore);
        attemptMapper.insert(attempt);
        return attempt.getId();
    }

    @Override
    @Transactional
    public void saveAnswers(Long attemptId, KpAnswerSaveReq req, Long userId) {
        KpExamAttempt attempt = ownedAttempt(attemptId, userId);
        // 只有作答中的卷子能改作答；判分中/已完成是历史，不能回改
        if (!"answering".equals(attempt.getStatus())) {
            throw new CoreCraftException(ResultCodeEnum.CONFLICT, "当前状态不能修改作答");
        }
        // PUT 全量覆盖：先清后写（答案物理删，见实施计划 §1.4）
        answerMapper.delete(Wrappers.<KpExamAnswer>lambdaQuery()
                .eq(KpExamAnswer::getAttemptId, attemptId));
        for (KpAnswerSaveReq.Item item : req.getAnswers()) {
            KpExamAnswer answer = new KpExamAnswer();
            answer.setAttemptId(attemptId);
            answer.setQuestionId(item.getQuestionId());
            answer.setAnswerPayload(JsonUtils.toJsonString(item.getAnswerPayload()));
            answer.setGradeRevision(0);
            answerMapper.insert(answer);
        }
    }

    @Override
    @Transactional
    public void submit(Long attemptId, Long userId) {
        KpExamAttempt attempt = ownedAttempt(attemptId, userId);
        if (!"answering".equals(attempt.getStatus())) {
            throw new CoreCraftException(ResultCodeEnum.CONFLICT, "当前状态不能交卷");
        }
        KpExamAttempt patch = new KpExamAttempt();
        patch.setId(attemptId);
        patch.setStatus("grading");
        patch.setSubmittedAt(AppClock.now());
        attemptMapper.updateById(patch);
    }

    @Override
    public KpAttemptResp selectAttempt(Long attemptId, Long userId) {
        KpExamAttempt attempt = ownedAttempt(attemptId, userId);
        KpExamPaper paper = paperMapper.selectById(attempt.getPaperId());
        KpAttemptResp resp = new KpAttemptResp();
        resp.setId(attempt.getId());
        resp.setPaperId(attempt.getPaperId());
        resp.setPaperTitle(paper == null ? "" : paper.getTitle());
        resp.setReplayOfAttemptId(attempt.getReplayOfAttemptId());
        resp.setStatus(attempt.getStatus());
        resp.setStartedAt(attempt.getStartedAt());
        resp.setSubmittedAt(attempt.getSubmittedAt());
        resp.setCompletedAt(attempt.getCompletedAt());
        resp.setScore(attempt.getScore());
        resp.setMaxScore(attempt.getMaxScore());
        return resp;
    }

    /** 归属校验的 canonical 出口：判分 / 回看等相邻服务共用（见接口注释） */
    @Override
    public KpExamAttempt ownedAttempt(Long attemptId, Long userId) {
        KpExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null || !Objects.equals(attempt.getUserId(), userId)) {
            throw new CoreCraftException(ResultCodeEnum.NOT_FOUND, "作答记录不存在");
        }
        return attempt;
    }
}
