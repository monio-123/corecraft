package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一次作答。**重做 = 新建 attempt 指向原记录**（{@link #replayOfAttemptId}），
 * 原记录不动，不做卷子去重（设计 §10.2.1）。
 *
 * <p>不软删（历史记录，与答案一样物理留痕）。⚠️ 不继承 BaseEntity：无 is_delete 列。
 */
@Getter
@Setter
@TableName("kp_exam_attempt")
public class KpExamAttempt {

    private Long id;

    private Long paperId;

    private Long userId;

    /** 重做指向原 attempt；NULL = 首次作答 */
    private Long replayOfAttemptId;

    /** {@code answering} 作答中 / {@code grading} 判分中 / {@code partial} 部分判定 / {@code completed} 完成 / {@code failed} 失败 */
    private String status;

    /** 开始作答时间 */
    private LocalDateTime startedAt;

    /** 交卷时间 */
    private LocalDateTime submittedAt;

    /** 判分完成时间 */
    private LocalDateTime completedAt;

    /** 实得分；partial 时只算已判定题，未判定不计入也不落 0（NULL = 还没分） */
    private BigDecimal score;

    private BigDecimal maxScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
