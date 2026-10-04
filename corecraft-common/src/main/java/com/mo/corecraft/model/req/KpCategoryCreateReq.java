package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 新建目录。同 (user, name) 唯一，重名由后端拒绝。 */
@Getter
@Setter
public class KpCategoryCreateReq {

    @NotBlank
    @Size(max = 50)
    private String name;

    /** 父目录 id，0（缺省）= 顶层 */
    private Long parentId;
}
