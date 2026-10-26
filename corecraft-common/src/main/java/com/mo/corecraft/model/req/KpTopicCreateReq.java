package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
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
    /** 知识点类型，指向 sys_dict_item.id（纯数字）。见 KpTopic.typeId */
    private Long typeId;

    private List<String> tags;
}
