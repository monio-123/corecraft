package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学习投影：**每用户每知识点一行**。
 *
 * <p>算法字段（ability / effectiveEventWeight / reviewCount …）**全是投影缓存**，
 * 唯一权威来源是 {@link KpMasteryEvidence} + {@link #learnedAt} 标记 ——
 * 「重放修复」可以安全覆盖它们，但 **{@link #learnedAt} 是用户显式操作，不能从证据反推**
 * （设计 §10.1）。删除知识点后本行软删隐藏，证据保留。
 *
 * <p>是否已学只看 {@code learnedAt} 非空，不另维护意思重复的布尔字段（设计 §3.1）。
 */
@Getter
@Setter
@TableName("kp_topic_learning")
public class KpTopicLearning extends BaseEntity {

    private Long id;

    private Long userId;

    private Long topicId;

    /** 标记已学时间；NULL = 未学 */
    @TableField(value = "learned_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime learnedAt;

    /**
     * 调度姿态（不是"是否已学"）：
     * {@code active} 正常调度 / {@code pending_initial} 待首次评估 /
     * {@code pending_confirm} 待确认当前状态 / {@code pending_reassessment} 待复测 /
     * {@code inactive} 停用（未学或取消已学）。
     */
    private String reviewState;

    /** 进入待办状态的原因（人看得懂；不靠时间字段猜用户为什么进队列）；NULL = 无待办原因 */
    @TableField(value = "pending_reason", updateStrategy = FieldStrategy.ALWAYS)
    private String pendingReason;

    /** 能力 A（0-100）投影缓存；NULL = 当前 epoch 未评估。权威在证据表 */
    @TableField(value = "ability", updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal ability;

    /** 可信度档：0=未评估 / 1=初步 / 2=较可信 / 3=稳定（判据见设计 §6.4） */
    private Integer confidenceLevel;

    /** 记忆稳定度 H（天）。半衰期≈复习间隔的简化：好表现翻倍封顶 180，薄弱减半保底 1 */
    private BigDecimal memoryStabilityDays;

    /** 最近具备回忆测量资格的主考证据时间（R(t) 的 t0）；NULL = 没有测量资格证据 */
    @TableField(value = "last_mastery_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastMasteryAt;

    /** 最近调度合格事件时间；NULL = 没有调度合格事件 */
    @TableField(value = "last_schedule_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastScheduleAt;

    /** 下次复习到期时间；NULL = 不调度（未学/取消已学） */
    @TableField(value = "next_review_at", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextReviewAt;

    /** 生命周期复习次数（调度合格事件累计） */
    private Integer reviewCount;

    /** 生命周期薄弱次数 */
    private Integer lapseCount;

    /** 当前 epoch 复习次数（重放只重建这一列） */
    private Integer epochReviewCount;

    /** 当前 epoch 薄弱次数 */
    private Integer epochLapseCount;

    /** 当前 epoch 有效事件权重之和（K 的原料；消费时封顶 4，这里存原值） */
    private BigDecimal effectiveEventWeight;

    /** 投影当前反映的正文 epoch（证据统一判据的投影侧） */
    private Integer activeContentEpoch;

    /** 投影由哪版算法重放得出 */
    private String algorithmVersion;

    /** 乐观锁版本号：多端同时答题/重放防互相覆盖 */
    private Long version;
}
