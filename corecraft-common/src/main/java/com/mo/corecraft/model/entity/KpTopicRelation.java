package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("kp_topic_relation")
public class KpTopicRelation extends BaseEntity {

    private Long id;

    @TableField("topic_id")
    private Long topicId;

    @TableField("related_topic_id")
    private Long relatedTopicId;

    @TableField("relation_type")
    private String relationType;
}
