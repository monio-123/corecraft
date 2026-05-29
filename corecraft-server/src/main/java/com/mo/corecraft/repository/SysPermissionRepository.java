package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysPermissionMapper;
import com.mo.corecraft.model.entity.SysPermission;
import com.mo.corecraft.model.query.SysPermissionQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

@Repository
public class SysPermissionRepository extends AbstractRepository<SysPermission, SysPermissionQuery, SysPermissionMapper>{

    public SysPermissionRepository(SysPermissionMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<SysPermissionQuery, LambdaQueryWrapper<SysPermission>> buildQueryWrapper(SysPermissionQuery query){
        var wrapper = new LambdaQueryWrapper<SysPermission>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(SysPermission::getId, id))
                .ifNotNull(query.getParentId(), (w, parentId) -> w.eq(SysPermission::getParentId, parentId))
                .ifNotBlank(query.getCode(), (w, code) -> w.eq(SysPermission::getCode, code))
                .ifNotBlank(query.getName(), (w, name) -> w.like(SysPermission::getName, name))
                .ifNotNull(query.getType(), (w, type) -> w.eq(SysPermission::getType, type))
                .ifNotNull(query.getEnabled(), (w, enabled) -> w.eq(SysPermission::isEnabled, enabled))
                .when(true, (w, q) -> w.orderByAsc(SysPermission::getSort).orderByAsc(SysPermission::getId));
    }

}
