package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不可变知识证据：**掌握度的唯一权威来源**。
 *
 * <p>不可变、**不软删**、永远保留；重新判分/重新归属产生新 revision 的新证据，
 * 旧证据打 {@link #supersededBy} 指针留痕，绝不物理改写历史（设计 §10.6）。
 * 全量重放顺序固定 {@code (assessed_at, attempt_id, id)}。
 *
 * <p>当前有效判据**只有一条**：{@code evidence.contentEpoch == topic.contentEpoch}；
 * hash 只负责 epoch 内一般编辑的可信度打折，任何接口不得另造「当前版本」定义（设计 §9.2）。
 *
 * <p>⚠️ 不继承 BaseEntity：无 is_delete 列，且**连 updateTime 都没有** —— 不可变。
 */
@Getter
@Setter
@TableName("kp_mastery_evidence")
public class KpMasteryEvidence {

    private Long id;

    /** 冗余列；挂证据前校验与 attempt/answer 一致，防跨用户挂证据 */
    private Long userId;

    private Long topicId;

    private Long attemptId;

    private Long answerId;

    /** 评分点 key；整题证据固定 {@code __question__}。非空刻意的：避免唯一键里 NULL 放行重复行 */
    private String rubricKey;

    /** 判分版本（证据诞生于哪一版判定） */
    private Integer gradeRevision;

    /** 归属版本（证据诞生于哪一版归属） */
    private Integer attributionRevision;

    /** 证据来源；v1 只有 {@code paper}（随堂/考试/复习卷同链路） */
    private String source;

    private BigDecimal scoreRatio;

    /** 知识归属权重：主考 1 / 辅考 <1；同题各考点之和 ≤1；unattributed 不产证据 */
    private BigDecimal topicWeight;

    /** 归属可靠性权重；hash 与证据不一致（epoch 内一般编辑）时打折，重放自然压低可信度 */
    private BigDecimal reliabilityWeight;

    /** 新颖性权重：跨卷撞车/重复提交打折。同卷重做是合法复习，只降新颖性、不撤调度资格 */
    private BigDecimal noveltyWeight;

    /** 计入能力 A 的资格 */
    private Boolean abilityEligible;

    /** 回忆测量资格（主考且测回忆）——推进 lastMasteryAt / R(t) */
    private Boolean retentionEligible;

    /** 推进复习调度的资格（H、nextReviewAt） */
    private Boolean scheduleEligible;

    /** 评估时点的知识点内容指纹快照 */
    private String contentHash;

    /** 评估时点的正文 epoch 快照；与 topic.contentEpoch 相等才算当前有效 */
    private Integer contentEpoch;

    /** 评估时间 = 用户提交作答时间（不取 AI 判分回调时间）；确定性重放的顺序锚点 */
    private LocalDateTime assessedAt;

    /** 被哪条新证据替代（重新判分/重新归属时打标）；NULL = 当前有效 */
    private Long supersededBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
