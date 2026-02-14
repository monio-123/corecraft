package com.mo.corecraft.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysRole extends BaseEntity {

    private Long id;

    private String code;

    private String name;

    @TableField("is_enabled")
    private boolean enabled;

    private String description;
}
