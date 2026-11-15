package com.mo.corecraft.service;

import com.mo.corecraft.model.entity.KpExamAttempt;
import com.mo.corecraft.model.req.KpAnswerSaveReq;
import com.mo.corecraft.model.req.KpAttemptCreateReq;
import com.mo.corecraft.model.resp.KpAttemptResp;

/**
 * 作答生命周期（设计 §11.2）：建 attempt → 保存作答 → 交卷。判分走 KpExamAnswerService。
 */
public interface KpExamAttemptService {

    Long createAttempt(KpAttemptCreateReq req, Long userId);

    void saveAnswers(Long attemptId, KpAnswerSaveReq req, Long userId);

    void submit(Long attemptId, Long userId);

    KpAttemptResp selectAttempt(Long attemptId, Long userId);

    /**
     * **归属校验的 canonical 出口**（查 + 验 userId，不是本人的按"不存在"处理）。
     * 判分 / 回看等相邻服务也走它——原来各写一份同形的私有 ownedAttempt，
     * 错误文案与校验口径会漂。
     */
    KpExamAttempt ownedAttempt(Long attemptId, Long userId);
}
