package com.mo.corecraft.model.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysRolePermission extends BaseEntity {

    private Long roleId;

    private Long permissionId;
}
