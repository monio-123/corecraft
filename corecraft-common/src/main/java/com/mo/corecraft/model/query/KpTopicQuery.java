package com.mo.corecraft.model.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KpTopicQuery {

    private Long id;

    private Long userId;

    private String title;

    private Long parentTopicId;

    private Long categoryId;
}
