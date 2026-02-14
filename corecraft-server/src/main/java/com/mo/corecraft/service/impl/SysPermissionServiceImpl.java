package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysPermission;
import com.mo.corecraft.model.query.SysPermissionQuery;
import com.mo.corecraft.model.resp.SysPermissionResp;
import com.mo.corecraft.repository.SysPermissionRepository;
import com.mo.corecraft.service.SysPermissionService;
import com.mo.corecraft.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermissionRepository sysPermissionRepository;

    @Override
    public SysPermissionResp selectSysPermission(SysPermissionQuery query) {
        return BeanUtils.createFrom(sysPermissionRepository.get(query), SysPermissionResp.class);
    }

    @Override
    public List<SysPermissionResp> selectSysPermissionList(SysPermissionQuery query) {
        return sysPermissionRepository.list(query).stream()
                .map(sysPermission -> BeanUtils.createFrom(sysPermission, SysPermissionResp.class)).toList();
    }

    @Override
    public IPage<SysPermissionResp> selectSysPermissionPage(Page<SysPermission> page, SysPermissionQuery query) {
        return sysPermissionRepository.page(page, query)
                .convert(sysPermission -> BeanUtils.createFrom(sysPermission, SysPermissionResp.class));
    }
}
