package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一道题的作答与判定。**按 {@link #gradeRevision} 留痕**：新 revision 才产生替代证据，
 * 历史 gradePayload 保留审计（设计 §10.5）。
 *
 * <p><b>物理删不用软删</b>（实施计划 §1.4）：唯一键 {@code (attempt_id, question_id)}
 * 撞不起软删墓碑。⚠️ 不继承 BaseEntity：无 is_delete 列。
 */
@Getter
@Setter
@TableName("kp_exam_answer")
public class KpExamAnswer {

    private Long id;

    private Long attemptId;

    private Long questionId;

    /** JSON：考生作答原文（选择项、文字答案） */
    private String answerPayload;

    /** 判定结果；NULL = 未判定。未判定不能落 0，不进 paperScore→mastery 的隐含零分路径 */
    private String verdict;

    /** 得分率 0~1；NULL = 未判定 */
    private BigDecimal scoreRatio;

    /** 实得分；NULL = 未判定 */
    private BigDecimal score;

    /** 讲解（判分产物，回看用） */
    private String explanation;

    /** JSON：评分点级判定快照；按 gradeRevision 留痕 */
    private String gradePayload;

    /** 判分版本，单调递增；0 = 未判 */
    private Integer gradeRevision;

    /** 判分回调幂等键：相同 resultId 幂等返回，重试不重复产证据 */
    private String resultId;

    /** 判分时间 */
    private LocalDateTime gradedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
