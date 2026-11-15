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

    /**
     * 标题 + 正文的归一化指纹（{@link com.mo.corecraft.utils.ContentHash}，任何内容变化都更新）。
     * <p>
     * 两条写路径都要维护它：{@code KpTopicServiceImpl.updateTopic}（一般编辑）和
     * {@code KpGenStepMapper.updateTopicContent}（AI 写正文）——只改一条就失效。
     * <p>
     * NULL = 存量行未计算（迁移时有意留空，SQL 里算不出和 Java 同口径的归一化），
     * 由写路径补算；证据生成前若为 NULL 必须先补算。
     */
    private String contentHash;

    /**
     * 重大正文版本号（初始 1）：**只有明确的大幅重写、重新生成才递增**，一般编辑不动它。
     * <p>
     * 「证据是否当前有效」的判据**只有** {@code evidence.contentEpoch == 本列}（设计 §9.2）。
     * epoch 切换时学习投影回待复测、A/M/H 回基线；hash 只负责 epoch 内一般编辑的可信度打折。
     */
    private Integer contentEpoch;

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

    /**
     * 是否参与掌握度的**单条覆盖**：NULL=继承类型默认 / TRUE=参与 / FALSE=不参与。
     *
     * <p>解析顺序只有一条（后端一处判据，见 {@code MasteryScope}）：
     * {@code 单条显式值 > 类型默认值（sys_dict_item.mastery_enabled）> 安全兜底（不参与）}。
     *
     * <p>⚠️ **字段名故意叫 {@code masteryOverride} 而不是 {@code masteryEnabled}**：
     * 库里存的是**可空的原始值**（还没解析），而响应里的 {@code masteryEnabled}
     * 是**已解析的最终判据**。两者同名 ⇒ BeanUtils 转换时会把原始值灌进最终值那一位，
     * 而最终值必须由判据算出来 ⇒ 两处同名必然串位。
     *
     * <p>⚠️ **这里必须用 {@code updateStrategy = ALWAYS}**：NULL 在这一列有真实含义
     * （"没配置，继承类型"），而 MP 默认的 NOT_NULL 策略会把 null 从 UPDATE 里跳过
     * ⇒ "恢复成继承类型"这个操作永远写不进去（列在、值还在，静默失效）。
     */
    @TableField(value = "mastery_enabled", updateStrategy = FieldStrategy.ALWAYS)
    private Boolean masteryOverride;
}
