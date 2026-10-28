package com.mo.corecraft.model.req;

import com.mo.corecraft.exception.CoreCraftException;
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

    /** 两次密码一致才算数（**抛 `CoreCraftException`**：走全局处理器的专用分支，带业务码） */
    public void validatePasswordEqual() {
        if (!password.equals(confirmPassword)) {
            throw new CoreCraftException("密码和确认密码不一致!");
        }
    }
}
