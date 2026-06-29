package com.mo.corecraft.utils;

import com.mo.corecraft.config.security.resource.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

public class SecurityUtil {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    static final Set<String> ADMIN_ROLES = Set.of(ROLE_ADMIN, "ADMIN");

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static SecurityUser getUser() {
        Authentication authentication = getAuthentication();
        if (authentication == null) {
            return new SecurityUser();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser) {
            return (SecurityUser) principal;
        }
        return new SecurityUser();
    }

    public static Set<String> getRoles() {
        return getUser().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    public static Set<String> getPermissions() {
        return Set.copyOf(getUser().getPermissions());
    }

    public static boolean isAdmin() {
        return ADMIN_ROLES.stream().anyMatch(getRoles()::contains);
    }

    public static boolean isAdminRoleCode(String code) {
        return ADMIN_ROLES.contains(code);
    }
}