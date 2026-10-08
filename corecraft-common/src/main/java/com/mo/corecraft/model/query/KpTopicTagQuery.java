package com.mo.corecraft.model.query;

import lombok.Getter;
import lombok.Setter;

/**
 * kp_topic_tag 查询条件。重构后仅按 topicId/tagId 查；category/name 已在 kp_tag 主表中。
 */
@Getter
@Setter
public class KpTopicTagQuery {

    private Long id;

    private Long topicId;

    private Long tagId;
}
