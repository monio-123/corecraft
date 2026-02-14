package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.mo.corecraft.enums.PermissionTypeEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysPermission extends BaseEntity {

    private Long id;

    private String code;

    private String name;

    private Integer sort;

    @TableField("is_enabled")
    private boolean enabled;

    private PermissionTypeEnum type;
}
