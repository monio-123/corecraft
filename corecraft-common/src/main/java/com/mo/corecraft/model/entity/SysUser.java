package com.mo.corecraft.model.entity;

import com.mo.corecraft.enums.UserStatusEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysUser extends BaseEntity {

    private String password;

    private String username;

    private UserStatusEnum status;

}
