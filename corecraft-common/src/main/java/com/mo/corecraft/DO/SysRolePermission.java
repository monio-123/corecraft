package com.mo.corecraft.DO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysRolePermission extends BaseDO {

    private Long roleId;

    private Long permissionId;
}
