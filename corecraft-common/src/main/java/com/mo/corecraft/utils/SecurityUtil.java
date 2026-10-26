package com.mo.corecraft.utils;

import com.mo.corecraft.config.security.resource.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录态读取工具。
 * <p>
 * <b>不设超级管理员机制</b>：这里没有任何"超管"判据，也没有角色编码常量。
 * 菜单可见性完全由 {@code sys_role_permission} 决定，接口鉴权由
 * {@code AuthCheck} 比对权限码完成。配错权限直接在数据库修，不在代码里留后门。
 */
public class SecurityUtil {

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

    /**
     * 当前登录用户的 id。**这里只做转发** —— 取数与「未登录返回 null」的规则都在
     * {@link com.mo.corecraft.config.security.resource.SecurityUser#getUserId()}（规则只存那一处）。
     */
    public static Long getUserId() {
        return getUser().getUserId();
    }
}