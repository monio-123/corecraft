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

    private String nickname;

    private String avatar;

    private String email;

    private String mobile;

    private AccountStatusEnum accountStatus;

}
