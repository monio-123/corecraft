package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条掌握度证据（回看用，人能读懂版）。
 */
@Getter
@Setter
public class KpEvidenceResp {

    private Long id;

    private Long attemptId;

    private Long answerId;

    /** 评分点 key；整题证据为 __question__ */
    private String rubricKey;

    /** paper 证据来源 */
    private String source;

    private BigDecimal scoreRatio;

    private BigDecimal topicWeight;

    private BigDecimal reliabilityWeight;

    private BigDecimal noveltyWeight;

    private Boolean abilityEligible;

    private Boolean retentionEligible;

    private Boolean scheduleEligible;

    private Integer contentEpoch;

    /** 评估时间 = 用户提交作答时间 */
    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime assessedAt;

    /** 非空 = 已被替代（留痕） */
    private Long supersededBy;

    private Integer gradeRevision;

    private Integer attributionRevision;
}
