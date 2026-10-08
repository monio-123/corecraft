package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("kp_topic_quiz")
public class KpTopicQuiz extends BaseEntity {

    private Long id;

    @TableField("topic_id")
    private Long topicId;

    @TableField("question_type")
    private String questionType;

    private String question;

    private String options;

    private Integer answer;

    private Integer sort;
}
