package com.mo.corecraft.config.security.resource;

import com.mo.corecraft.utils.SecurityUtil;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * 接口级鉴权表达式，供 {@code @PreAuthorize("@auth.hasPermission('xxx')")} 使用。
 * <p>
 * 权限码存在 {@code sys_permission.code}，经 {@code sys_role_permission} 分配到角色，
 * 再由 {@code SysUserMapper.selectByUsername} 装载进 principal 的 permissions 集合。
 * <p>
 * <b>注意</b>：权限码没有进 Spring 的 authorities（那里只装角色编码），
 * 所以内置的 {@code hasAuthority} / {@code hasPermission} 对权限码无效，
 * 必须像这里一样绕开 authorities 直接比对。
 */
@Component("auth")
public class AuthCheck {

    /**
     * 纯权限码判定，不看角色。
     * <p>
     * 代码里只出现权限码本身，码的定义与授权全在库里 —— 没有任何角色常量或后门。
     */
    public boolean hasPermission(String permission) {
        return SecurityUtil.getPermissions().contains(permission);
    }

    public boolean hasAnyRoleOrPermission(Collection<String> roles, Collection<String> permissions) {
        Set<String> userRoles = SecurityUtil.getRoles();
        boolean hasRole = roles.stream().anyMatch(userRoles::contains);
        if (hasRole) {
            return true;
        }
        return permissions.stream().anyMatch(SecurityUtil.getPermissions()::contains);
    }
}
