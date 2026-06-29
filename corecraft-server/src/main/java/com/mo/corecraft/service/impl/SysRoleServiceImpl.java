package com.mo.corecraft.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.config.cache.CacheKeys;
import com.mo.corecraft.config.cache.CacheManagerFacade;
import com.mo.corecraft.model.entity.SysRole;
import com.mo.corecraft.model.entity.SysRolePermission;
import com.mo.corecraft.model.query.SysRoleQuery;
import com.mo.corecraft.model.req.SysRoleCreateReq;
import com.mo.corecraft.model.req.SysRoleUpdateReq;
import com.mo.corecraft.model.resp.SysRoleResp;
import com.mo.corecraft.repository.SysRolePermissionRepository;
import com.mo.corecraft.repository.SysRoleRepository;
import com.mo.corecraft.service.SysRoleService;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleRepository sysRoleRepository;

    private final SysRolePermissionRepository sysRolePermissionRepository;

    @Override
    public SysRoleResp selectSysRole(SysRoleQuery query) {
        SysRoleResp resp = sysRoleRepository.get(query, BeanUtils.converter(SysRoleResp.class));
        resp.setPermissionIds(loadPermissionIds(resp.getId()));
        return resp;
    }

    @Override
    public List<SysRoleResp> selectSysRoleList(SysRoleQuery query) {
        return sysRoleRepository.list(query, BeanUtils.converter(SysRoleResp.class));
    }

    @Override
    public IPage<SysRoleResp> selectSysRolePage(Page<SysRole> page, SysRoleQuery query) {
        return sysRoleRepository.page(page, query, BeanUtils.converter(SysRoleResp.class));
    }

    @Override
    @Transactional
    public void createSysRole(SysRoleCreateReq req) {
        SysRole entity = new SysRole();
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setEnabled(Optional.ofNullable(req.getEnabled()).orElse(true));
        entity.setDescription(req.getDescription());
        sysRoleRepository.insert(entity);
        sysRolePermissionRepository.saveRolePermissions(entity.getId(), req.getPermissionIds());
    }

    @Override
    @Transactional
    public void updateSysRole(SysRoleUpdateReq req) {
        sysRoleRepository.update(req.getId(), req, (r, e) -> {
            if (StringUtils.isNotBlank(r.getCode())) {
                e.setCode(r.getCode());
            }
            if (StringUtils.isNotBlank(r.getName())) {
                e.setName(r.getName());
            }
            if (r.getEnabled() != null) {
                e.setEnabled(r.getEnabled());
            }
            if (r.getDescription() != null) {
                e.setDescription(r.getDescription());
            }
        });
        if (req.getPermissionIds() != null) {
            SysRole entity = sysRoleRepository.entityById(req.getId());
            if (SecurityUtil.isAdminRoleCode(entity.getCode())) {
                return;
            }
            sysRolePermissionRepository.saveRolePermissions(req.getId(), req.getPermissionIds());
            CacheManagerFacade.clear(CacheKeys.SECURITY_USER_RECORD);
        }
    }

    @Override
    @Transactional
    public void deleteSysRole(Long id) {
        SysRole entity = sysRoleRepository.entityById(id);
        if (SecurityUtil.isAdminRoleCode(entity.getCode())) {
            throw new RuntimeException("超级管理员角色不允许删除");
        }
        sysRolePermissionRepository.deleteByRoleId(id);
        sysRoleRepository.delete(id);
        CacheManagerFacade.clear(CacheKeys.SECURITY_USER_RECORD);
    }

    @Override
    public void deleteBatchSysRole(List<Long> ids) {
        ids.forEach(sysRolePermissionRepository::deleteByRoleId);
        sysRoleRepository.deleteBatch(ids);
        CacheManagerFacade.clear(CacheKeys.SECURITY_USER_RECORD);
    }

    private List<Long> loadPermissionIds(Long roleId) {
        return sysRolePermissionRepository.listByRoleId(roleId).stream()
                .map(SysRolePermission::getPermissionId)
                .toList();
    }
}
