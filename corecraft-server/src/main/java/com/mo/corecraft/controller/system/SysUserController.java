package com.mo.corecraft.controller.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.config.security.resource.SecurityUser;
import com.mo.corecraft.model.req.SysUserPasswordUpdateReq;
import com.mo.corecraft.model.req.SysUserProfileUpdateReq;
import com.mo.corecraft.model.req.SysUserRoleAssignReq;
import com.mo.corecraft.model.resp.SysPermissionResp;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.model.req.SysUserCreateReq;
import com.mo.corecraft.model.req.SysUserUpdateReq;
import com.mo.corecraft.model.resp.SysUserProfileResp;
import com.mo.corecraft.model.resp.ResultResp;
import com.mo.corecraft.model.resp.SysUserResp;
import com.mo.corecraft.service.SysPermissionService;
import com.mo.corecraft.service.SysUserService;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/sys/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final SysPermissionService sysPermissionService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResultResp<SysUserResp> sysUser(SysUserQuery query) {
        return ResultResp.data(sysUserService.selectSysUser(query));
    }

    @RequestMapping(value = "list", method = RequestMethod.GET)
    public ResultResp<List<SysUserResp>> sysUserList(SysUserQuery query) {
        return ResultResp.data(sysUserService.selectSysUserList(query));
    }

    @RequestMapping(value = "page", method = RequestMethod.GET)
    public ResultResp<IPage<SysUserResp>> sysUserPage(SysUserQuery query,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResultResp.data(sysUserService.selectSysUserPage(Page.of(page, pageSize), query));
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    // 样本接口：演示"权限码 → 库内定义与授权"的接口鉴权能力。
    // user:add 是 OP 类型权限，挂在「用户管理」菜单下，由 sys_role_permission 决定谁有。
    // 本期只有这一个端点接入该体系，其余接口暂不接入。
    @PreAuthorize("@auth.hasPermission('user:add')")
    public ResultResp<Void> createSysUser(@Validated @RequestBody SysUserCreateReq req) {
        sysUserService.createSysUser(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "", method = RequestMethod.PUT)
    public ResultResp<Void> updateSysUser(@Validated @RequestBody SysUserUpdateReq req) {
        sysUserService.updateSysUser(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}/role", method = RequestMethod.PUT)
    public ResultResp<Void> assignRole(@PathVariable("id") Long id, @RequestBody SysUserRoleAssignReq req) {
        sysUserService.assignUserRole(id, req == null ? null : req.getRoleId());
        return ResultResp.success();
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    public ResultResp<Void> deleteSysUser(@PathVariable("id") Long id) {
        sysUserService.deleteSysUser(id);
        return ResultResp.success();
    }

    @RequestMapping(value = "batch", method = RequestMethod.DELETE)
    public ResultResp<Void> deleteBatchSysUser(@RequestParam List<Long> ids) {
        sysUserService.deleteBatchSysUser(ids);
        return ResultResp.success();
    }

    @RequestMapping(value = "me/permissions", method = RequestMethod.GET)
    public ResultResp<Map<String, Object>> myPermissions() {
        SecurityUser user = SecurityUtil.getUser();
        return ResultResp.data(Map.of(
                "authenticated", user.isAuthenticated(),
                "username", user.getUsername(),
                "roles", SecurityUtil.getRoles(),
                "permissions", SecurityUtil.getPermissions()
        ));
    }

    @RequestMapping(value = "me/menu-tree", method = RequestMethod.GET)
    public ResultResp<List<SysPermissionResp>> myMenuTree() {
        // 菜单完全由 sys_role_permission 决定，没有"超管绕过"分支——
        // 配错权限直接在数据库修，不在代码里开后门
        Set<String> permissions = SecurityUtil.getPermissions();
        return ResultResp.data(sysPermissionService.selectMenuTreeByPermissions(permissions));
    }

    @RequestMapping(value = "me/profile", method = RequestMethod.GET)
    public ResultResp<SysUserProfileResp> myProfile() {
        return ResultResp.data(sysUserService.currentUserProfile());
    }

    @RequestMapping(value = "me/profile", method = RequestMethod.PUT)
    public ResultResp<Void> updateMyProfile(@RequestBody SysUserProfileUpdateReq req) {
        sysUserService.updateCurrentUserProfile(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "me/password", method = RequestMethod.PUT)
    public ResultResp<Void> updateMyPassword(@Validated @RequestBody SysUserPasswordUpdateReq req) {
        sysUserService.updateCurrentUserPassword(req);
        return ResultResp.success();
    }

    @RequestMapping(value = "me/avatar", method = RequestMethod.POST)
    public ResultResp<Map<String, String>> uploadMyAvatar(@RequestParam("file") MultipartFile file) {
        String avatar = sysUserService.uploadCurrentUserAvatar(file);
        return ResultResp.data(Map.of("avatar", avatar));
    }

}
