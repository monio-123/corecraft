package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 目录重命名：oldName → newName。后端校验 newName 未被该 user 占用。 */
@Getter
@Setter
public class KpCategoryRenameReq {

    @NotBlank
    @Size(max = 50)
    private String oldName;

    @NotBlank
    @Size(max = 50)
    private String newName;
}
