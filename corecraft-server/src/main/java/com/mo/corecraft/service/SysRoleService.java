package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysRole;
import com.mo.corecraft.model.query.SysRoleQuery;
import com.mo.corecraft.model.resp.SysRoleResp;

import java.util.List;

public interface SysRoleService {

    SysRoleResp selectSysRole(SysRoleQuery query);

    List<SysRoleResp> selectSysRoleList(SysRoleQuery  query);

    IPage<SysRoleResp> selectSysRolePage(Page<SysRole> page, SysRoleQuery query);
}
