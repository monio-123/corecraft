package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysRoleMapper;
import com.mo.corecraft.model.entity.SysRole;
import com.mo.corecraft.model.query.SysRoleQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

@Repository
public class SysRoleRepository extends AbstractRepository<SysRole, SysRoleQuery, SysRoleMapper>{

    public SysRoleRepository(SysRoleMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<SysRoleQuery, LambdaQueryWrapper<SysRole>> buildQueryWrapper(SysRoleQuery query){
        var wrapper = new LambdaQueryWrapper<SysRole>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(SysRole::getId, id))
                .ifNotBlank(query.getCode(), (w, code) -> w.eq(SysRole::getCode, code))
                .ifNotBlank(query.getName(), (w, name) -> w.like(SysRole::getName, name))
                .ifNotNull(query.getEnabled(), (w, enabled) -> w.eq(SysRole::isEnabled, enabled))
                .when(true, (w, q) -> w.orderByAsc(SysRole::getId));
    }

    public SysRole entityById(Long id) {
        return mapper.selectById(id);
    }

}
