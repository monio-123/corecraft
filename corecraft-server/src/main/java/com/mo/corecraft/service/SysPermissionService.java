package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysPermission;
import com.mo.corecraft.model.query.SysPermissionQuery;
import com.mo.corecraft.model.req.SysPermissionCreateReq;
import com.mo.corecraft.model.req.SysPermissionUpdateReq;
import com.mo.corecraft.model.resp.SysPermissionResp;

import java.util.Collection;
import java.util.List;

public interface SysPermissionService {

    SysPermissionResp selectSysPermission(SysPermissionQuery query);

    List<SysPermissionResp> selectSysPermissionList(SysPermissionQuery  query);

    IPage<SysPermissionResp> selectSysPermissionPage(Page<SysPermission> page, SysPermissionQuery query);

    List<SysPermissionResp> selectSysPermissionTree(SysPermissionQuery query);

    List<SysPermissionResp> selectMenuTreeByPermissions(Collection<String> permissionCodes, boolean includeAll);

    void createSysPermission(SysPermissionCreateReq req);

    void updateSysPermission(SysPermissionUpdateReq req);

    void deleteSysPermission(Long id);

    void deleteBatchSysPermission(List<Long> ids);
}
