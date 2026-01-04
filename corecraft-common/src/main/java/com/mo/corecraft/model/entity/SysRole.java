package com.mo.corecraft.model.entity;

import com.mo.corecraft.enums.StatusEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysRole extends BaseEntity {

    private String code;

    private String name;

    private StatusEnum status;

    private String description;
}
