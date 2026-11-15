package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学习投影（设计 §10.1）：学过标记 + 掌握度两个展示维度 + 复习调度。
 *
 * <p>展示拆两维（设计 §6.2）：{@code ability + confidenceLevel} 是「能力掌握」（不衰减），
 * {@code freshness} 是「记忆新鲜度」（R，触发“该复习了”）。内部 M = A × R 只做队列排序，不做情绪化档位。
 */
@Getter
@Setter
public class KpLearningResp {

    private Long topicId;

    /** 非空即已学 */
    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime learnedAt;

    /** active / pending_initial / pending_confirm / pending_reassessment / inactive */
    private String reviewState;

    private String pendingReason;

    /** 能力 A（0-100）；null = 当前 epoch 未评估 */
    private BigDecimal ability;

    /** 0=未评估 / 1=初步 / 2=较可信 / 3=稳定 */
    private Integer confidenceLevel;

    /**
     * 能力掌握展示档位（设计 §6.5）：待复测 / 未评估 / 稳定掌握 / 初步掌握 / 待巩固 / 基本掌握 / 熟练。
     * 阈值在 {@code MasteryCalculator.masteryLevel} 一处，前端只渲染这个词、不复制阈值。
     */
    private String masteryLevel;

    /** 记忆新鲜度 R（0~1）：距上次回忆测量的衰减 */
    private BigDecimal freshness;

    /** 记忆稳定度 H（天） */
    private BigDecimal memoryStabilityDays;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime lastMasteryAt;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime nextReviewAt;

    private Integer reviewCount;

    private Integer lapseCount;

    private Integer activeContentEpoch;

    private String algorithmVersion;
}
