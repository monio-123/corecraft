package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysUserRoleMapper;
import com.mo.corecraft.model.entity.SysUserRole;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SysUserRoleRepository {

    private final SysUserRoleMapper mapper;

    public SysUserRoleRepository(SysUserRoleMapper mapper) {
        this.mapper = mapper;
    }

    public List<SysUserRole> listByUserId(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
    }

    public void deleteByUserId(Long userId) {
        mapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
    }

    public void saveUserRole(Long userId, Long roleId) {
        deleteByUserId(userId);
        if (roleId == null) {
            return;
        }
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        mapper.insert(relation);
    }
}

