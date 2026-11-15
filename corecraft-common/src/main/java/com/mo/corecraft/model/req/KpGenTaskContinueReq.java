package com.mo.corecraft.model.req;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.Valid;
import java.util.List;

/**
 * **同一个任务继续往下跑**（确认之后接着生成正文）。
 *
 * <p>为什么要有它：v2 设计里「生成主题」是**一张卡、三个节点**
 * （生成目录 → 确认 → 生成正文）。用户确认完目录之后，
 * 正文那一步的步骤要**挂在同一个任务上**接着跑 —— 而不是新建第二张卡
 * （两张卡 ⇒ 用户看到"目录任务"和"正文任务"两个互不相干的东西，
 * 而且"生成主题"这件事被拆成了两半）。
 *
 * <p>⚠️ **步骤由前端算好传上来**（复用 `buildMaterial`）：
 * 后端不理解材料语义，两份定义必然漂 ⇒ 材料只有前端那一份。
 */
@Getter
@Setter
public class KpGenTaskContinueReq {

    /**
     * 要接着写的正文步骤（一条 = 一次 AI 调用）。
     *
     * <p>空 ⇒ 报错（没有要写的东西就别占用一次确认）。
     */
    private List<KpGenTaskCreateReq.Step> steps;

    /**
     * 「建结构」这一步的摘要（**老链路**：前端已经建好了目录，服务端只补一条完成记录）。
     *
     * <p>⚠️ 2026-11-13 起**优先走 {@link #nodes}（服务端建目录）**；这条只服务
     * 前端还没切换的路径，两边别混着传（传了 nodes 就忽略 buildSummary）。
     */
    private String buildSummary;

    /**
     * **确认过的目录树**（服务端建目录用，2026-11-13 起）。
     *
     * <p>用户在弹窗里核对的树原样传上来 —— 建目录、建知识点占位都由服务端做，
     * 作为任务链上的「建目录」节点（有步骤、有耗时）。⇒ 确认之后**暂停 / 恢复
     * 都不会重建**（以前建在前端：确认完没提交就关弹窗，任务还停在待确认，
     * 下次点确认又建一遍 —— 用户实测踩到「重复了都」）。
     */
    @Valid
    private List<KpCategoryImportNode> nodes;

    /** 树挂在哪个已有目录下（0 / null = 根下） */
    private Long mountParentId;

    /**
     * 要用的 AI 端点（可空）。
     *
     * <p>⚠️ **给不给都行**：给了就改写参数快照里的端点。
     * 规划跑完到写正文可能隔了很久，用户那时可能已经换了端点
     * （比如规划用便宜模型、正文用强模型）⇒ 让前端显式传一次。
     */
    private Long endpointId;
}
