package com.mo.corecraft.service;

import com.mo.corecraft.model.req.KpLearningMarkReq;
import com.mo.corecraft.model.resp.KpCategoryLearningResp;
import com.mo.corecraft.model.resp.KpEvidenceResp;
import com.mo.corecraft.model.resp.KpLearningResp;
import com.mo.corecraft.model.resp.KpTopicLevelResp;

import java.util.List;

/**
 * 学习状态（设计 §3 / §10.1）：学过标记是显式操作，掌握度是投影。
 */
public interface KpTopicLearningService {

    /** 标记 / 取消已学（状态机见设计 §3.2） */
    void markLearning(Long topicId, KpLearningMarkReq req, Long userId);

    /** 学习投影（能力 / 可信度 / 新鲜度 / 调度） */
    KpLearningResp selectLearning(Long topicId, Long userId);

    /** 该知识点的证据列表（回看；被替代的也留痕返回） */
    List<KpEvidenceResp> selectEvidence(Long topicId, Long userId);

    /** 目录三指标聚合（设计 §8.1）：一次批量返回整棵树，不让前端逐目录请求 */
    List<KpCategoryLearningResp> selectTree(Long rootCategoryId, Long userId);

    /**
     * 全部有评估的知识点的档位与掌握度值（2026-11-14，目录树知识点行的新数据源）。
     * 行数 = 考过的知识点数（量小）；无评估的行不返回。
     */
    List<KpTopicLevelResp> selectMasteryLevels(Long userId);
}
