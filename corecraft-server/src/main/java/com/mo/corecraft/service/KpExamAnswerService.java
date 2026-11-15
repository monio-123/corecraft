package com.mo.corecraft.service;

import com.mo.corecraft.model.req.KpAttributionSaveReq;
import com.mo.corecraft.model.req.KpGradeResultReq;
import com.mo.corecraft.model.resp.KpAttemptReviewResp;

/**
 * 判分结果与回看（设计 §11.2 / §11.3）：resultId 幂等，新 gradeRevision 才产替代证据。
 */
public interface KpExamAnswerService {

    /** 判分结果回调：成绩、证据、学习投影、复习调度在同一事务提交 */
    void gradeResult(Long attemptId, KpGradeResultReq req, Long userId);

    /** 逐题回看：题干 + 作答 + 判定 + 讲解 */
    KpAttemptReviewResp selectReview(Long attemptId, Long userId);

    /** 保存题目的知识归属（出题后独立的弱归属一步）；revision 服务端单调递增 */
    void saveAttribution(Long questionId, KpAttributionSaveReq req, Long userId);
}
