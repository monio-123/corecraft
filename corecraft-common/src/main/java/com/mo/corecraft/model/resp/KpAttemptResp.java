package com.mo.corecraft.model.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.corecraft.model.DateTimeFormats;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一次作答（答卷头）。partial 时 score 只算已判定题，未判定不计入也不落 0。
 */
@Getter
@Setter
public class KpAttemptResp {

    private Long id;

    private Long paperId;

    private String paperTitle;

    /** 非空 = 这是一次重做 */
    private Long replayOfAttemptId;

    /** answering / grading / partial / completed / failed */
    private String status;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime startedAt;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime submittedAt;

    @JsonFormat(pattern = DateTimeFormats.DATETIME, timezone = DateTimeFormats.TIMEZONE)
    private LocalDateTime completedAt;

    private BigDecimal score;

    private BigDecimal maxScore;
}
