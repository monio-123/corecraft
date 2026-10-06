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

    /**
     * 知识点类型（单选），指向 {@code sys_dict_item.id}。
     * <p>
     * 与目录、标签并列的第四个正交维度，回答的是「这条知识是什么性质」——
     * 随手记 / 系统学。解决的是"记的太多，想找成体系的那部分却筛不出来"。
     * <p>
     * 存的是**字典项 id（纯数字）**而不是名字或 0/1/2 编码：字典项增删排序都不影响
     * 任何代码，代码里也不存在"1 = 笔记"这种要同步维护的映射。
     * <p>
     * 没有外键约束：字典项是软删的（{@code is_delete}），加外键会在字典管理里
     * 删项时把删除卡住。查不到对应字典项时由前端兜底成"排第一的那档"。
     * <p>
     * 这里**不用** {@code updateStrategy = ALWAYS}：类型没有"清空=某个含义"的语义，
     * MP 默认的 NOT_NULL 策略正好合适——没传就跳过。
     */
    @TableField("type_id")
    private Long typeId;
}
