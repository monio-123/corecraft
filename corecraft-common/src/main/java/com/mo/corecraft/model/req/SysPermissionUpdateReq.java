package com.mo.corecraft.model.req;

import com.mo.corecraft.enums.PermissionTypeEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysPermissionUpdateReq {

    @NotNull(message = "id不能为空!")
    private Long id;

    private Long parentId;

    private String code;

    private String name;

    private Integer sort;

    private Boolean enabled;

    private PermissionTypeEnum type;

    private String meta;
}
