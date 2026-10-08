package com.mo.corecraft.model.query;

import lombok.Getter;
import lombok.Setter;
import com.mo.corecraft.enums.PermissionTypeEnum;

@Getter
@Setter
public class SysPermissionQuery {

    private Long id;

    private Long parentId;

    private String code;

    private String name;

    private PermissionTypeEnum type;

    private Boolean enabled;
}
