package com.mo.corecraft.model.req;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KpTopicCreateReq {

    private String title;

    private String content;

    private Long parentTopicId;

    private Long categoryId;

    /**
     * 是否参与掌握度的**单条覆盖**：null=跟随默认 / true=参与 / false=不参与。
     */
    private Boolean masteryEnabled;

    private List<String> tags;
}
