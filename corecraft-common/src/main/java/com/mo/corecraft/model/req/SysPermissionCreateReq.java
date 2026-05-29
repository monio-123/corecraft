package com.mo.corecraft.model.req;

import com.mo.corecraft.enums.PermissionTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysPermissionCreateReq {

    private Long parentId;

    @NotBlank(message = "权限编码不能为空!")
    private String code;

    @NotBlank(message = "权限名称不能为空!")
    private String name;

    private Integer sort;

    private Boolean enabled;

    @NotNull(message = "权限类型不能为空!")
    private PermissionTypeEnum type;

    private String meta;
}
