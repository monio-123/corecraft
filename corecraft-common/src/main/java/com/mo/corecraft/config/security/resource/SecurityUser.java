package com.mo.corecraft.config.security.resource;

import com.mo.corecraft.config.interceptor.UserDataScope;
import com.mo.corecraft.model.dto.SysUserDTO;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;

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
    private UserDataScope dataScope;

    @Setter
    @Builder.Default
    private Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();

    @Setter
    @Builder.Default
    private Collection<String> permissions = new ArrayList<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public Collection<String> getPermissions() {
        return permissions;
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
