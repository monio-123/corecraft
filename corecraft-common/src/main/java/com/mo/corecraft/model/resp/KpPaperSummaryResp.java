package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试历史列表摘要（设计 §11.3）：**不返回完整题干**——列表页不该拖全卷。
 */
@Getter
@Setter
public class KpPaperSummaryResp {

    private Long id;

    private String kind;

    private Long categoryId;

    private String title;

    private String status;

    private Integer questionCount;

    /** 最近一次作答 id（无作答为空） */
    private Long lastAttemptId;

    /** 最近一次作答得分 */
    private BigDecimal lastScore;

    private BigDecimal lastMaxScore;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime lastSubmittedAt;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime createTime;
}
