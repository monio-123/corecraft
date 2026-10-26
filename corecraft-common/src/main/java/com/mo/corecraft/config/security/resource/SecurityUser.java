package com.mo.corecraft.config.security.resource;

import com.mo.corecraft.model.dto.SysUserDTO;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityUser implements UserDetails {

    @Setter
    private String password;

    @Setter
    private SysUserDTO sysUserDTO;

    @Setter
    @Builder.Default
    private Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();

    @Setter
    @Builder.Default
    private Collection<String> permissions = new ArrayList<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities == null ? List.of() : authorities;
    }

    public Collection<String> getPermissions() {
        return permissions == null ? List.of() : permissions;
    }

    /**
     * 当前登录用户的 id。
     *
     * <p>**为什么放在域对象上**：全项目所有 controller / service 取「当前用户 id」都走这一处
     * （{@code SecurityUtil.getUserId()} 只是转发）—— 散着写就意味着「登录态没了怎么办」
     * 这条规则要改几十处 ⇒ 收敛到一处，调用方一行拿到结果，不用自己判空。
     *
     * <p>未登录时返回 {@code null}（**不是 0**）—— 调用方要么已经登录态、要么本来就该
     * 报「未登录」，返回 0 会和「真的有一个 id=0 的用户」混淆。
     */
    public Long getUserId() {
        return sysUserDTO == null ? null : sysUserDTO.getId();
    }

    public boolean isAuthenticated() {
        return sysUserDTO != null;
    }

    @Override
    public String getUsername() {
        return sysUserDTO != null ? sysUserDTO.getUsername() : null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
