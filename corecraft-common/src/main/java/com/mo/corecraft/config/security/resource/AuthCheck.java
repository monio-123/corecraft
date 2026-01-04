package com.mo.corecraft.config.security.resource;

import com.mo.corecraft.utils.SecurityUtil;
import org.springframework.stereotype.Component;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.stream.Collectors;

@Component("auth") // 在 SpEL 表达式中使用 @auth 调用
public class AuthCheck {

    public boolean hasAnyRoleOrPermission(Collection<String> roles, Collection<String> permissions) {
        SecurityUser user = SecurityUtil.getUser();
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        var authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        boolean hasRole = roles.stream().anyMatch(authorities::contains);
        if (hasRole) {
            return true;
        }
        return permissions.stream().anyMatch(user.getPermissions()::contains);
    }


    public boolean hasRoleOrPermission(String role, String permission) {
        SecurityUser user = SecurityUtil.getUser();
        if (user == null || user.getAuthorities() == null) {
            return false;
        }
        // 先判断角色
        boolean hasRole = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
        if (hasRole) {
            return true;
        }
        // 再判断权限
        return user.getPermissions() != null && user.getPermissions().contains(permission);
    }
}
