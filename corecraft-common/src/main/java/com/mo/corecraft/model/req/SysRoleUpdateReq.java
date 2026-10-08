package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SysRoleUpdateReq {

    @NotNull(message = "id不能为空!")
    private Long id;

    private String code;

    private String name;

    private Boolean enabled;

    private String description;

    private List<Long> permissionIds;
}
