package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 目录重命名请求：把 id 那一行的 name 改成 newName。
 *
 * 只收 id 不收 oldName：名字是可变的字段，拿它当定位键等于"改名那一刻就换了身份证"。
 * 允许同名之后更明显——同一层两个同名目录时，按 oldName 根本认不出要改的是哪一个。
 */
@Getter
@Setter
public class KpCategoryRenameReq {

    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 50)
    private String newName;
}
