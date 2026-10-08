package com.mo.corecraft.model.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KpTopicRelationQuery {

    private Long id;

    private Long topicId;

    private Long relatedTopicId;
}
