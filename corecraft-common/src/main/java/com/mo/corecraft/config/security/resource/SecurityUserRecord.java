package com.mo.corecraft.config.security.resource;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;

@Getter
@Setter
public class SecurityUserRecord {

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private String mobile;

    /**
     * 集合**必须非空**：这条记录会被直接塞进 {@code SecurityUser} 的 builder
     * （见 {@code UserDetailsServiceImpl}），而 builder 的 {@code $set} 标志位会让
     * 「显式传 null」**覆盖**掉 {@code @Builder.Default} 的空集合初始化 ⇒
     * 后面 {@code getRoles().stream()} 直接 NPE。
     */
    private Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();

    private Collection<String> permissions = new ArrayList<>();
}
