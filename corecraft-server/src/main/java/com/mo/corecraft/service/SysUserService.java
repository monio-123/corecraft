package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.model.req.SysUserCreateReq;
import com.mo.corecraft.model.req.SysUserUpdateReq;
import com.mo.corecraft.model.resp.SysUserResp;

import java.util.List;

public interface SysUserService {

    SysUserResp selectSysUser(SysUserQuery  query);

    List<SysUserResp> selectSysUserList(SysUserQuery  query);

    IPage<SysUserResp> selectSysUserPage(Page<SysUser> page, SysUserQuery query);

    void createSysUser(SysUserCreateReq req);

    void updateSysUser(SysUserUpdateReq req);
}
