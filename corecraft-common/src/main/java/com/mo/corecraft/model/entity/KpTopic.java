package com.mo.corecraft.model.entity;

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

    @TableField("tree_id")
    private Long treeId;
}
