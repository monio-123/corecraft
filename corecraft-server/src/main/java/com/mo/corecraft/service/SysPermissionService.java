package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysPermission;
import com.mo.corecraft.model.entity.SysRole;
import com.mo.corecraft.model.query.SysPermissionQuery;
import com.mo.corecraft.model.query.SysRoleQuery;
import com.mo.corecraft.model.resp.SysPermissionResp;
import com.mo.corecraft.model.resp.SysRoleResp;

import java.util.List;

public interface SysPermissionService {

    SysPermissionResp selectSysPermission(SysPermissionQuery query);

    List<SysPermissionResp> selectSysPermissionList(SysPermissionQuery  query);

    IPage<SysPermissionResp> selectSysPermissionPage(Page<SysPermission> page, SysPermissionQuery query);
}
