package com.mo.corecraft.service;

import com.mo.corecraft.model.req.KpClassifyAdoptReq;

/**
 * AI 归类的**采纳**。
 *
 * <p>为什么单独一个 service：归类任务的**跑**在 {@link KpGenTaskService} 里
 * （和写正文共用同一套任务中心），但**采纳**是另一件事 ——
 * 它要写 kp_topic（标题 / 目录 / 标签），那属于 {@link KpTopicService} 的地盘。
 *
 * <p>⚠️ **采纳不是"跑"的延续**：跑的时候只把建议存起来，
 * 写库只发生在人点了「采纳」之后 —— 目录归属是**需要人工确认的主观判断**。
 */
public interface KpClassifyService {

    /**
     * 采纳一批归类建议。
     *
     * @param req 要采纳哪些、改成什么
     * @param userId 归属（步骤和知识点都必须归本人）
     * @return 实际写库的条数
     */
    int adopt(KpClassifyAdoptReq req, Long userId);
}
