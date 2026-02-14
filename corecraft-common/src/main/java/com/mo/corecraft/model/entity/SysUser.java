package com.mo.corecraft.model.entity;

import com.mo.corecraft.enums.AccountStatusEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysUser extends BaseEntity {

    private Long id;

    private String password;

    private String username;

    private AccountStatusEnum accountStatus;

}
