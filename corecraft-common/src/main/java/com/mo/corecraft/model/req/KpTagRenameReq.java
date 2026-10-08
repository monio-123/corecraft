package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 标签重命名请求：把 oldName 改为 newName。
 * 后端会校验 newName 未被该 user 占用。
 */
@Getter
@Setter
public class KpTagRenameReq {

    @NotBlank
    @Size(max = 50)
    private String oldName;

    @NotBlank
    @Size(max = 50)
    private String newName;
}
