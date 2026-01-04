package com.mo.corecraft.model.entity;

import com.mo.corecraft.enums.PermissionTypeEnum;
import com.mo.corecraft.enums.StatusEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysPermission extends BaseEntity {

    private String code;

    private String name;

    private Integer sort;

    private StatusEnum status;

    private PermissionTypeEnum type;
}
