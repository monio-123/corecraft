package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysUserPasswordUpdateReq {

    @NotBlank(message = "旧密码不能为空!")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空!")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空!")
    private String confirmPassword;
}
