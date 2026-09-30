package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识点 ↔ 标签 纯关联表。
 * <p>
 * 不存 category/name（冗余），改 tag 名直接改 {@link KpTag} 即可。
 */
@Getter
@Setter
@TableName("kp_topic_tag")
public class KpTopicTag extends BaseEntity {

    private Long id;

    @TableField("topic_id")
    private Long topicId;

    @TableField("tag_id")
    private Long tagId;
}
