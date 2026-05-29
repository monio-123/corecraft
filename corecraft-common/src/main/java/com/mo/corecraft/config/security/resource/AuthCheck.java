package com.mo.corecraft.config.security.resource;

import com.mo.corecraft.utils.SecurityUtil;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

@Component("auth")
public class AuthCheck {

    public boolean hasAnyRoleOrPermission(Collection<String> roles, Collection<String> permissions) {
        Set<String> userRoles = SecurityUtil.getRoles();
        boolean hasRole = roles.stream().anyMatch(userRoles::contains);
        if (hasRole) {
            return true;
        }
        return permissions.stream().anyMatch(SecurityUtil.getPermissions()::contains);
    }

    public boolean hasRoleOrPermission(String role, String permission) {
        Set<String> userRoles = SecurityUtil.getRoles();
        if (userRoles.contains(role)) {
            return true;
        }
        return SecurityUtil.getPermissions().contains(permission);
    }
}
