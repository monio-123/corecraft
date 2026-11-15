package com.mo.corecraft.service;

import java.util.Set;

/**
 * 掌握度证据与投影重放（设计 §5 / §6 / §10.6）：证据不可变，投影可重放重建。
 */
public interface KpMasteryEvidenceService {

    /**
     * 重新生成某次作答的证据（判分 / 归属更新后调用）：
     * 本次 attempt 旧证据整体打 superseded_by 留痕，按当前归属 + 当前判定重建。
     */
    void regenerateFromAttempt(Long attemptId);

    /** 按证据表全量重放，重建指定知识点集合的学习投影（幂等，可安全重复跑） */
    void recomputeTopics(Set<Long> topicIds, Long userId);

    /**
     * 重放该用户的**全部**学习投影（幂等，可安全重复跑）。
     *
     * <p>两个用途：
     * <ul>
     *   <li>存量回填：2026-11-12 之前的学习投影 {@code learned_at} 全是 NULL
     *       （那时只有"标记已学"按钮能写它），改成"有证据即已学"后需要重放一次
     *       才能把 {@code learned_at} 补上，否则目录"已学数"永远是 0；</li>
     *   <li>算法升级：{@link com.mo.corecraft.service.support.MasteryCalculator#ALGORITHM_VERSION}
     *       变更后，把所有人的投影按新算法重算一遍。</li>
     * </ul>
     *
     * @return 实际重放的知识点数量
     */
    int recomputeAllTopics(Long userId);
}
