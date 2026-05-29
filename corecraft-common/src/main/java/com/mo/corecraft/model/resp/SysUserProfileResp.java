package com.mo.corecraft.model.resp;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
public class SysUserProfileResp {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String email;

    private String mobile;

    private List<String> roles;

    private Set<String> permissions;
}
