package com.mo.corecraft.model.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysUserRole extends BaseEntity {

    private Long userId;

    private Long roleId;
}
