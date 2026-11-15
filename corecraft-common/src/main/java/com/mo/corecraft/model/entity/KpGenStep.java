package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务里的一步 = **一次 AI 调用**。
 *
 * <p>为什么按调用记、不按目录记：「一次生成」不是一个目录的属性，而是**一批**；
 * 正文还可能几天后由另一个入口补写、再被人改过 ⇒ 目录上挂一个批次 id 答不了
 * 「这条正文是哪一次调用写的」。按调用记，三个问题都能答。
 *
 * <p>{@link #promptSnapshot} 存的是**实际发给模型的那份 messages 原文** ——
 * 后端 `AiDelta.prompt` 在开流前本来就原样发过一次，直接存这份，不重拼：
 * 重拼一次就多一次偏差的机会，而"配置里是什么"证明不了"实际发出去的是什么"。
 */
@Getter
@Setter
@TableName("kp_gen_step")
public class KpGenStep extends BaseEntity {

    private Long id;

    private Long taskId;

    /** 从 1 开始，决定执行顺序与"从哪继续" */
    private Integer seq;

    /**
     * 所属**阶段**（2026-11-12 阶段化）：{@code skeleton} / {@code expand} /
     * {@code verify} / {@code confirm} / {@code build} / {@code write} / {@code finish}。
     *
     * <p>为什么要按阶段分组：任务从"一根进度条"改成"一棵可读的执行树" ——
     * 用户要知道「现在在哪一段、这一段在干什么」，而不是"已处理 12/64"。
     * 阶段也为**并行度**和**人工断点**提供挂点（见设计笔记：任务 → 阶段 → 步骤）。
     */
    private String phase;

    /** 阶段内序号（1 起）。**不替代 {@link #seq}**：seq 是全局执行顺序，这个是组内显示顺序 */
    private Integer phaseSeq;

    /**
     * **一句话输出摘要**（列表直接显示，不用点开）。
     *
     * <p>形如「5 段 2613 字 · 图 1 表 1」「4 节 18 点」。存摘要而不是让前端算：
     * 前端拿不到"段名几个、图几张"（那要读正文），而写库这一刻这些东西都在手上。
     */
    private String outputSummary;

    /** 1 = 建议人工复核（结构下限没通过 / 撞上限截断）。**不阻断流程**，只是举手机 */
    private Integer needConfirm;

    /** 这一步叫什么（知识点标题 / 题号…） —— 出错时给用户看的就是它 */
    private String title;

    /** 结果写到哪条知识点（content 类型用；出卷这类产物不落 kp_topic 的留空） */
    private Long topicId;

    /** {@code pending} / {@code running} / {@code ok} / {@code failed} / {@code skipped} */
    private String status;

    /**
     * 喂给模型的材料（前端用 `buildMaterial` 算好传上来的）。
     *
     * <p>**落库**是为了断点续写与重跑：前端不用再算一遍，
     * "任务创建那一刻的树"也才有据可查（之后目录被移动、标题被改都不影响这次重跑）。
     */
    private String material;

    /** 实际发给模型的 messages 原文（提示词快照） */
    private String promptSnapshot;

    /**
     * 这一步的**产物**（JSON 文本）。
     *
     * <p>为什么现在才有这一列（2026-11-11）：这张表原来只存**输入**（material /
     * prompt_snapshot）和**计量**（token / 耗时），因为唯一在跑的任务类型是
     * {@code content}（写正文）—— 它的产物直接落进 {@code kp_topic}，步骤表不必留答案。
     *
     * <p>{@code classify}（AI 归类）不一样：它的产物是**建议**，而"目录归属"是
     * **需要人工确认的主观判断** ⇒ 建议不能自动写库，得先存起来等人采纳。
     *
     * <p>⚠️ **存原始回答，不存解析后的结构**：解析规则（"名字：/目录：/标签："怎么读）
     * 在前端只有一份，而且解析结果**依赖当下的目录清单** —— 同一个
     * "目录：数据库/MySQL"，今天这个目录存在、明天被删了，解析结果就不一样。
     * ⇒ 存原始文本、采纳时现解析，才是"采纳时按当下目录清单重新校验"的前提。
     *
     * <p>{@code content} 类型留空（产物在 {@code kp_topic} 里）。
     */
    private String result;

    private Integer contentLen;

    /** 拿不到 usage 就留空 —— **不本地估算后当真数用** */
    private Integer tokenPrompt;

    private Integer tokenCompletion;

    private Integer durationMs;

    /** {@code stop} 正常写完；{@code length} **撞了输出上限，内容是截断的** */
    private String finishReason;

    private String error;
}
