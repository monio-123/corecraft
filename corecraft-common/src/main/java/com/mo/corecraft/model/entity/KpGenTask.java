package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 一个生成任务。**一次「生成主题 / 出卷 / 判分」= 一行**。
 *
 * <p>存在的理由只有一条：**执行不能住在浏览器里**。循环搬到服务端之后，
 * 「跑到哪了 / 为什么停了 / 重跑会不会不一样」必须有人记得 —— 就是这个表。
 *
 * <p>{@link #promptLen} / {@link #promptMd5} 是**提示词指纹**：提示词在 sys_config 里
 * 随时会改，重跑时先把指纹和当前配置对一下，就知道"这次重跑和上次不是同一个提示词"。
 */
@Getter
@Setter
@TableName("kp_gen_task")
public class KpGenTask extends BaseEntity {

    private Long id;

    private Long userId;

    /**
     * 任务类型：{@code content} = 写正文；以后 {@code outline} / {@code exam_paper} /
     * {@code exam_grade} 都挂这一个表 —— 前端任务面板按类型换文案即可，不再各做一套。
     */
    private String type;

    /** 人看得懂的任务名（主题 / 目录 / 卷名） */
    private String title;

    /**
     * {@code queued} 排队 → {@code running} 执行 → {@code succeeded} / {@code failed}；
     * 用户点停止 ⇒ {@code canceling}，执行器在**每一条开始前**看到它就收手，收尾置 {@code canceled}。
     */
    private String status;

    /**
     * 当前**阶段** key（2026-11-12 阶段化）。卡片上显示「阶段 2/4」靠它。
     *
     * <p>{@code awaiting} 状态也是同一批改动加的：任务跑到一个人工断点会**停下来等人**，
     * 既不是失败也不是完成。⚠️ 启动清理**不能**把 awaiting 标中断（见 markOrphans）。
     */
    private String stage;

    private Integer total;

    /** 已尝试过的步数（成功 + 失败）。进度条读它，**不是**成功数。 */
    private Integer doneCount;

    private Integer failedCount;

    /** 参数快照 JSON（主题 / 目录 / 开关 / 模型 / 是否思考 / maxTokens）。重跑要一模一样就靠它 */
    private String params;

    /** 提示词配置键（如 {@code ai.prompt.knowledge.point}） */
    private String promptKey;

    private Integer promptLen;

    private String promptMd5;

    /** 结果摘要 JSON（给前端展示用） */
    private String result;

    private String error;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    /**
     * **人工节点办完的时间**（采纳 / 确认）。
     *
     * <p>⚠️ 为什么需要单独一列：人工节点原来靠「后继节点已经有步骤」判"已越过"，
     * 而**链尾的人工节点没有后继**（AI 归类跑完 → 确认/采纳）⇒ 永远判不出"人办完了"，
     * 卡片会一直停在「待确认」。这一列就是那件事的记录。
     *
     * <p>它**对所有任务类型通用**：任何类型的人工节点被办完都写它，
     * 不需要按类型各配一套"算不算完成"的规则。
     */
    private LocalDateTime confirmedAt;
}
