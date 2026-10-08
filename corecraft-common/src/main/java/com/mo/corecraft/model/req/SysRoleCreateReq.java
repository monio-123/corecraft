package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SysRoleCreateReq {

    @NotBlank(message = "角色编码不能为空!")
    private String code;

    @NotBlank(message = "角色名称不能为空!")
    private String name;

    private Boolean enabled;

    private String description;

    private List<Long> permissionIds;
}
