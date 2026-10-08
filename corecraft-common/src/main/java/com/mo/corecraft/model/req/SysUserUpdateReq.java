package com.mo.corecraft.model.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SysUserUpdateReq {

    @NotNull(message = "id不能为空")
    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private String mobile;
}
