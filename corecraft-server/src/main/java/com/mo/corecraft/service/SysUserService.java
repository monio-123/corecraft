package com.mo.corecraft.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.model.resp.SysUserResp;

import java.util.List;

public interface SysUserService {

    SysUserResp selectUser(SysUserQuery  query);

    List<SysUserResp> selectUserList(SysUserQuery  query);

    IPage<SysUserResp> selectUserPage(Page<SysUser> page, SysUserQuery query);
}
