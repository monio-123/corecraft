package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务（列表 + 详情共用）。
 *
 * <p>列表接口**不填 `steps`**（几百步 + 每步材料，会把列表撑爆）；
 * 只有详情接口带。
 */
@Getter
@Setter
public class KpGenTaskResp {

    private Long id;

    private String type;

    private String title;

    /** queued / running / awaiting / succeeded / failed / canceling / canceled */
    private String status;

    /** 当前阶段 key（skeleton / expand / verify / confirm / write …） */
    private String stage;

    /**
     * **当前节点 key**（outline / confirm / body / classify …）。
     *
     * <p>和 {@link #stage} 的区别：stage 是**节点内部的步骤组**（在"骨架"这一步），
     * nodeKey 是**节点**（在"生成目录"这个节点）。两者同时给，前端不用自己推。
     */
    private String nodeKey;

    /**
     * **节点链**（随任务一起下发）。
     *
     * <p>⚠️ **节点定义只在后端有一份**（{@code GenTaskNodes}）——
     * 前端若也维护一份，两处必然漂（症状：卡片上三个节点、点进去只有一个有内容）。
     * ⇒ 前端只渲染这个数组 ⇒ **加一种任务类型不用改前端**。
     */
    private List<Node> nodes;

    private Integer total;

    private Integer doneCount;

    private Integer failedCount;

    /** 参数快照（原样回前端，任务详情里展示"这次是什么参数"） */
    private String params;

    private String error;

    /**
     * 结果摘要 JSON。**目前只有目录任务用**（{@code outline} + 统计 + 校验问题）。
     *
     * <p>只详情接口带：一棵 232 节点的树约 15KB，列表接口传它没有意义。
     */
    private String result;

    private LocalDateTime createTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    /** 只详情接口有 */
    private List<Step> steps;

    /**
     * 一个**节点**（任务 → 节点 → 步骤组 → 步骤 的第二层）。
     *
     * <p>状态口径和步骤同一套：{@code pending} / {@code running} / {@code ok} /
     * {@code failed} / {@code awaiting}（等人）/ {@code skipped}（被跳过）。
     */
    @Getter
    @Setter
    public static class Node {

        /** 节点 key（outline / confirm / body …） */
        private String key;

        /** 节点名（「生成目录」） */
        private String name;

        /** 这一步是干嘛的（「AI 把主题拆成目录树」）—— 属于协议，后端下发 */
        private String desc;

        /** pending / running / ok / failed / awaiting / skipped */
        private String status;

        /** 已完成的步骤数 */
        private Integer done;

        /** 该节点的步骤总数 */
        private Integer total;

        /** 这一段已经花掉的时间（毫秒；人工节点恒 0） */
        private Long elapsedMs;

        /** 一句话事实摘要（「8 模块 · 344 点」） */
        private String summary;

        /** 节点内部的步骤组（每组一行小结） */
        private List<Group> groups;
    }

    /** 节点里的一个步骤组（「逐章展开 6/8」） */
    @Getter
    @Setter
    public static class Group {

        /** 组 key（skeleton / expand / verify …） */
        private String key;

        /** 组名 */
        private String name;

        /** 已完成条数 */
        private Integer done;

        /** 总条数 */
        private Integer total;

        /** 正在跑的条数 */
        private Integer running;

        /** 失败的条数 */
        private Integer failed;
    }

    @Getter
    @Setter
    public static class Step {

        private Long id;

        private Integer seq;

        private String title;

        private Long topicId;

        private String status;

        /** 所属阶段 + 阶段内序号（前端按它分组，不靠 seq 推） */
        private String phase;

        private Integer phaseSeq;

        /** 一句话输出摘要（「5 段 2613 字 · 图 1 表 1」） */
        private String outputSummary;

        /** 1 = 建议人工复核 */
        private Integer needConfirm;

        private Integer contentLen;

        private Integer tokenPrompt;

        private Integer tokenCompletion;

        private Integer durationMs;

        /** stop / length（length = 截断） */
        private String finishReason;

        private String error;

        /**
         * 这一步的**产物**（JSON 文本）。AI 归类任务的步骤才有。
         *
         * <p>⚠️ **详情接口才带**：产物可能有几百 KB（一条回答），
         * 列表接口带上它就等于把整批回答都传一遍。
         */
        private String result;
    }
}
