package com.mo.corecraft.model.req;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysUserProfileUpdateReq {

    private String nickname;

    private String email;

    private String mobile;

    private String avatar;
}
