package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷中的一道题：**不可变快照 + 归属快照**。
 *
 * <p>不软删、随卷子走（删卷 = 软删整张卷，题目不单独删）。需要查询计算的关系
 * 在 {@link KpMasteryEvidence} 规范化，不靠扫 JSON 算掌握度（设计 §10.3）。
 *
 * <p>⚠️ 本表刻意**不继承 BaseEntity**：没有 is_delete 列（不软删），
 * 继承了会让 MP 每条查询都带 {@code is_delete = 0} ⇒ Unknown column。
 */
@Getter
@Setter
@TableName("kp_exam_question")
public class KpExamQuestion {

    private Long id;

    private Long paperId;

    /** 卷内题号（稳定标识靠 id，题号只管展示顺序） */
    private Integer questionNo;

    /** 题型 code；全站唯一一份题型表在前端 examParse.EXAM_TYPES，落库前先归一化 */
    private String type;

    private BigDecimal maxScore;

    /** JSON：题干、选项、参考答案、评分点、场景与认知层次（当次 AI 产物快照） */
    private String questionPayload;

    /** 题干归一化哈希（与 contentHash 同源算法）；跨卷撞车比对用，只存哈希不存题干副本 */
    private String questionFingerprint;

    /** JSON：题目级/评分点级知识归属、权重、归属方式与原因。按 attributionRevision 留痕 */
    private String attributionPayload;

    /** {@code pending} / {@code attributed} / {@code partial} / {@code unattributed} / {@code failed} */
    private String attributionStatus;

    /** 归属版本，单调递增；0 = 尚未归属。判分消费哪一版由当前指针明确 */
    private Integer attributionRevision;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
