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
    /** 知识点类型，指向 sys_dict_item.id（纯数字）。见 KpTopic.typeId */
    private Long typeId;

    /**
     * 是否参与掌握度的**单条覆盖**：null=继承类型默认 / true=参与 / false=不参与。
     * 新建时一般不传（继承类型就够了）；留这个口子是为了"新建时直接定死"这类用法。
     */
    private Boolean masteryEnabled;

    private List<String> tags;
}
