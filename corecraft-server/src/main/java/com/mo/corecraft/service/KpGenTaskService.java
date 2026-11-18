package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.KpGenTask;
import com.mo.corecraft.model.req.KpGenTaskContinueReq;
import com.mo.corecraft.model.req.KpGenTaskCreateReq;
import com.mo.corecraft.model.resp.KpGenTaskResp;

/**
 * 生成任务中心。
 *
 * <p>**唯一的存在理由**：把「几百条 AI 调用」从浏览器标签页里搬出来。
 * 页面关了、网断了、电脑睡了，任务照跑；回来打开任务列表就能看进度、接着补没写完的。
 *
 * <p>方法一律带 `userId`，自己取归属 —— 跟 `AiChatService` 一个规矩：
 * 不碰 `SecurityUtil`（那是 web 层的事），执行器跑在别的线程上更拿不到。
 */
public interface KpGenTaskService {

    /**
     * 建任务并**立刻开始执行**（排队跑，不阻塞请求线程）。
     *
     * @return 任务 id（前端拿它去看进度）
     */
    Long create(Long userId, KpGenTaskCreateReq req);

    /**
     * **暂停**（2026-11-12 用户口径：叫「暂停」不叫「停止」——
     * 叫「停止」会让用户以为"彻底不跑了、不能恢复"）。
     *
     * <p>**不立刻杀线程** —— 只把状态置 `canceling`，
     * 执行器在**每一条开始前**看到它就收手（正在写的那一条会写完，不会落半截）。
     *
     * <p>暂停后可以「恢复」，也可以「删除」。
     */
    void cancel(Long id, Long userId);

    /**
     * 继续跑：把 `pending` / `failed` 的步骤重置成待执行，然后重新提交。
     *
     * <p>**重试单位是「步」不是「任务」** —— 300 条里挂了 3 条，重跑只该补那 3 条。
     *
     * <p>⚠️ **目录任务也能恢复**（2026-11-12）：骨架原文与已展开章节的原文
     * 都存在步骤的 `result` 里 ⇒ 恢复时直接复用，**不重出骨架、不产生重复章节**。
     */
    void resume(Long id, Long userId);

    /** 任务列表（**不带 steps**） */
    IPage<KpGenTaskResp> page(Page<KpGenTask> page, Long userId, String status, String type);

    /** 任务详情（带 steps；材料/提示词快照不回传，它们可能几 MB） */
    KpGenTaskResp detail(Long id, Long userId);

    /**
     * **确认人已经接手**（目录任务专用）：把 {@code awaiting} 收成 {@code succeeded}。
     *
     * <p>为什么需要它：任务停在 awaiting 是在等"人把树建进知识库"。
     * 建完之后必须有人告诉任务"这一趟结束了"，否则任务中心里会一直挂着一个
     * 「待你确认」—— 挂久了，用户就分不清哪个是真等他。
     */
    void ack(Long id, Long userId);

    /**
     * **同一个任务继续往下跑**（v2 单任务化）。
     *
     * <p>用户确认完目录之后，正文那一步**挂在同一个任务上**接着跑，
     * 而不是新建第二张卡（两张卡 ⇒ 「生成主题」被拆成两半，用户看不到全貌）。
     *
     * <p>只有 {@code awaiting} 的任务能这样继续（它在等人确认）；
     * 已经跑完的任务再调 ⇒ 报错（那是重跑，语义不同）。
     */
    void continueTask(Long id, Long userId, KpGenTaskContinueReq req);

    /**
     * **重跑某个节点**（节点级重试）。
     *
     * <p>为什么按节点而不是按任务：目录跑完了、正文跑一半挂了，
     * 用户想补的是**正文**那个节点 —— 让整个任务从头再来一遍会把目录也重跑一遍
     * （4 分钟 + 一堆额度），而且目录已经确认过了。
     *
     * @param nodeKey 要重跑的节点（outline / body / classify …）
     */
    void retryNode(Long id, Long userId, String nodeKey);

    /**
     * **删除任务**（软删）。任务卡上的「⋯ → 删除」。
     *
     * <p>⚠️ **只删任务本身**：已经写进知识库的正文**留在库里** ——
     * "删掉这个任务"≠"删掉生成的知识"。目录任务停在 awaiting 时，
     * 产物还没落库，删掉它就是"这棵树我不要了"。
     */
    void remove(Long id, Long userId);

    /** 批量清理当前用户所有已完成的任务，返回清理条数 */
    int removeFinished(Long userId);
}
