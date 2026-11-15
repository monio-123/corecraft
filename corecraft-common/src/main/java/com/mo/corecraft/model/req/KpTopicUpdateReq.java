package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KpTopicUpdateReq {

    @NotNull(message = "id不能为空!")
    private Long id;

    private String title;

    private String content;

    private Long parentTopicId;

    private Long categoryId;
    /** 知识点类型，指向 sys_dict_item.id（纯数字）。见 KpTopic.typeId */
    private Long typeId;

    /**
     * 是否参与掌握度的**单条覆盖**：null=继承类型默认 / true=参与 / false=不参与。
     *
     * <p>⚠️ **null 在这里有含义**（"继承类型"），所以必须能写进库
     * （KpTopic.masteryOverride 标了 updateStrategy = ALWAYS）。
     * 与 SysDictItem 那一列的 null 语义**不同**（那边 null = 不修改）。
     */
    private Boolean masteryEnabled;

    private List<String> tags;
}
