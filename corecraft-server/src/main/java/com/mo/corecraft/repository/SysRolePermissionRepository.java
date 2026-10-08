package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysRolePermissionMapper;
import com.mo.corecraft.model.entity.SysRolePermission;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class SysRolePermissionRepository {

    private final SysRolePermissionMapper mapper;

    public SysRolePermissionRepository(SysRolePermissionMapper mapper) {
        this.mapper = mapper;
    }

    public List<SysRolePermission> listByRoleId(Long roleId) {
        return mapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
    }

    public void deleteByRoleId(Long roleId) {
        mapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
    }

    public void saveRolePermissions(Long roleId, Collection<Long> permissionIds) {
        deleteByRoleId(roleId);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        permissionIds.stream()
                .distinct()
                .map(permissionId -> {
                    SysRolePermission relation = new SysRolePermission();
                    relation.setRoleId(roleId);
                    relation.setPermissionId(permissionId);
                    return relation;
                })
                .forEach(mapper::insert);
    }
}
