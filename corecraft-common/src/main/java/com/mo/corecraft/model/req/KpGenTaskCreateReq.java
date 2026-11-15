package com.mo.corecraft.model.req;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 建一个生成任务。
 *
 * <p>**任务 = 一串「材料 → 一次调用 → 一个落库动作」**：前端把每一步的材料算好
 * （复用 `buildMaterial`），后端只负责按序调用、落库、记进度。
 * 后端**不理解材料语义**，所以同一套任务中心能直接接出卷 / 判分。
 *
 * <p>⚠️ 材料由前端算，不是为了省事：材料拼装在 JS 里已经有一份
 * （`utils/knowledgePoints#buildMaterial`），在 Java 里再实现一份**必然漂移**
 * —— 两份定义打架时，症状是"生成的正文和界面上看到的材料对不上"，几乎无法排查。
 */
@Getter
@Setter
public class KpGenTaskCreateReq {

    /** 任务类型，默认写正文。以后 outline / exam_paper / exam_grade 共用这一张表 */
    private String type = "content";

    /** 人看得懂的任务名（主题 / 目录名） */
    private String title;

    /** 提示词配置键（`ai.prompt.knowledge.point` 等），执行器每一调用都带它 */
    private String promptKey;

    /**
     * 参数快照（原样存库、执行时再解析）：
     * `endpointId` / `thinking` / `maxTokens` / 主题 / 开关……
     *
     * <p>不拆成一个个字段：任务类型一多，字段就会长成一张只有一半列有值的宽表。
     *
     * <p>⚠️ **两种形状都收**（2026-11-15 修「随堂重新生成 400」）：
     * 对象 `{...}` / JSON 字符串 `"{...}"`——调用方历史上两种都在发
     * （主题生成 stringify、考试任务化传对象），接口不该挑形状，
     * 落库前由服务端统一归一成 JSON 字符串。
     */
    private Object params;

    private List<Step> steps;

    @Getter
    @Setter
    public static class Step {

        /** 从 1 开始 */
        private Integer seq;

        /** 所属阶段（content 任务默认 write；阶段化后由前端或后端给） */
        private String phase;

        /** 阶段内序号（不给就按 seq 走） */
        private Integer phaseSeq;

        private String title;

        /**
         * 结果写到哪条知识点。**content 类型必填**；
         * 「生成主题」确认后的那一批**可以不带**（树还没落库、占位还没建）——
         * 服务端建完占位自己解析（2026-11-13，配合 {@code dirPath}）。
         */
        private Long topicId;

        /**
         * 这条属于哪个目录（路径 {@code a/b/c}）。只在**不带 topicId** 的一批里有意义：
         * 服务端建目录 + 占位后按它把步骤对到知识点上。
         */
        private String dirPath;

        private String material;
    }
}
