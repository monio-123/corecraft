package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysRole;
import com.mo.corecraft.model.query.SysRoleQuery;
import com.mo.corecraft.model.resp.SysRoleResp;
import com.mo.corecraft.repository.SysRoleRepository;
import com.mo.corecraft.service.SysRoleService;
import com.mo.corecraft.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleRepository sysRoleRepository;

    @Override
    public SysRoleResp selectSysRole(SysRoleQuery query) {
        return BeanUtils.createFrom(sysRoleRepository.get(query), SysRoleResp.class);
    }

    @Override
    public List<SysRoleResp> selectSysRoleList(SysRoleQuery query) {
        return sysRoleRepository.list(query).stream()
                .map(sysRole -> BeanUtils.createFrom(sysRole, SysRoleResp.class)).toList();
    }

    @Override
    public IPage<SysRoleResp> selectSysRolePage(Page<SysRole> page, SysRoleQuery query) {
        return sysRoleRepository.page(page, query)
                .convert(sysRole -> BeanUtils.createFrom(sysRole, SysRoleResp.class));
    }
}
