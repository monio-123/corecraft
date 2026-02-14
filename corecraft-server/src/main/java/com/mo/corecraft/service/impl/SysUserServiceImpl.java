package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.model.req.SysUserCreateReq;
import com.mo.corecraft.model.req.SysUserUpdateReq;
import com.mo.corecraft.model.resp.SysUserResp;
import com.mo.corecraft.repository.SysUserRepository;
import com.mo.corecraft.service.SysUserService;
import com.mo.corecraft.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService{

    private final SysUserRepository sysUserRepository;

    @Override
    public SysUserResp selectSysUser(SysUserQuery query) {
        return BeanUtils.createFrom(sysUserRepository.get(query), SysUserResp.class);
    }

    @Override
    public List<SysUserResp> selectSysUserList(SysUserQuery query) {
        return sysUserRepository.list(query).stream()
                .map(sysUser -> BeanUtils.createFrom(sysUser, SysUserResp.class)).toList();
    }

    @Override
    public IPage<SysUserResp> selectSysUserPage(Page<SysUser> page, SysUserQuery query) {
        return sysUserRepository.page(page, query)
                .convert(sysUser -> BeanUtils.createFrom(sysUser, SysUserResp.class));
    }

    @Override
    public void createSysUser(SysUserCreateReq req) {

    }

    @Override
    public void updateSysUser(SysUserUpdateReq req) {

    }
}
