package com.mo.corecraft.model.req;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysUserProfileUpdateReq {

    @Size(max = 20, message = "用户名长度不能超过 20 个字符")
    private String username;

    private String nickname;

    @Size(max = 30, message = "邮箱长度不能超过 30 个字符")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过 20 个字符")
    private String mobile;

    private String avatar;
}
