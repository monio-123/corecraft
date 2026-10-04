package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识目录（树的骨架）。
 * <p>
 * 与 {@link KpTag} 是两个正交概念，别混用：
 * 目录 = 归属（这条知识属于哪），单选互斥，是树视图的节点；
 * 标签 = 关联（这条知识涉及什么），多选平铺，只做检索筛选。
 * <p>
 * 刻意<b>不继承</b> {@link BaseEntity}：BaseEntity 带 {@code @TableLogic}，软删会在
 * uk_user_name 唯一键上留墓碑，导致"删过的目录名"永久无法重建。目录没有独立价值
 * （删了就是没了），物理删更干净。代价是本表没有 is_delete 列。
 */
@Getter
@Setter
@TableName("kp_category")
public class KpCategory {

    /** 顶层目录的 parent_id */
    public static final Long ROOT_ID = 0L;

    private Long id;

    @TableField("user_id")
    private Long userId;

    private String name;

    /**
     * 父目录。0 = 顶层，null 已归一到 0。
     * <p>
     * 表里的历史行是 NULL。顶层统一存 0 而不是 NULL：将来唯一键换成
     * {@code (user_id, parent_id, name)} 时，MySQL 的 UNIQUE 索引里 NULL
     * 不参与去重——两个同名顶层（都是 NULL）会同时插进去，去重形同虚设。
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 领域对象非空契约：父目录 id 永不返回 null，顶层恒为 0。
     * <p>
     * 收口在这里，调用方不需要各写一遍 {@code x == null ? 0 : x}。
     */
    public Long getParentId() {
        return parentId == null ? ROOT_ID : parentId;
    }

    // 目录表不继承 BaseEntity：它刻意没有 is_delete 列（目录走物理删），
    // 继承过去会带出一个映射不到列的 deleted 字段。代价是时间戳要自己挂 fill，
    // 好在 MetaObjectHandler 认的是字段名 + fill 策略，跟继承关系无关。
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
