package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("kp_topic")
public class KpTopic extends BaseEntity {

    private Long id;

    @TableField("user_id")
    private Long userId;

    private String title;

    private String content;

    @TableField("parent_topic_id")
    private Long parentTopicId;

    /**
     * 所属知识目录（单选互斥，null = 未归类）。
     * <p>
     * 替代旧的 tree_id —— 那列存的是 tagId.hashCode()，前端建树从来不读它，
     * 只有后端每次增删改白费力气重算一遍，已随 2026-10-03 迁移删除。
     * <p>
     * {@code updateStrategy = ALWAYS} 是必需的：null 在这里有真实含义（未归类），
     * 而 MP 默认的 NOT_NULL 策略会把 null 字段从 UPDATE 语句里跳过，
     * 导致"移回未归类"永远写不进去。
     */
    @TableField(value = "category_id", updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;
}
