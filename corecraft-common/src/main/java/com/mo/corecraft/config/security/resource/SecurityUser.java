package com.mo.corecraft.config.security.resource;

import com.mo.corecraft.model.dto.SysUserDTO;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Getter
@Setter
@Builder
public class SecurityUser implements UserDetails {

    private String password;

    private SysUserDTO sysUserDTO;

    /**
     * 角色集合
     */
    private Collection<SimpleGrantedAuthority> authorities;

    /**
     * 权限集合
     */
    private Collection<String> permissions;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return sysUserDTO.getUsername();
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
