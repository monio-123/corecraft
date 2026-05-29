package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysUserCreateReq {

    @NotBlank(message = "用户名不能为空!")
    private String username;

    @NotBlank(message = "密码不能为空!")
    private String password;

    @NotBlank(message = "确认密码不能为空!")
    private String confirmPassword;

    private String nickname;

    private String avatar;

    private String email;

    @NotBlank(message = "手机号码不能为空!")
    private String mobile;

    public void validatePasswordEqual() {
        if (!password.equals(confirmPassword)) {
            throw new RuntimeException("密码和确认密码不一致!");
        }
    }
}
