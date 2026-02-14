package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysPermissionMapper;
import com.mo.corecraft.model.dto.SysPermissionDTO;
import com.mo.corecraft.model.entity.SysPermission;
import com.mo.corecraft.model.query.SysPermissionQuery;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

@Repository
public class SysPermissionRepository extends AbstractRepository<SysPermission, SysPermissionDTO, SysPermissionQuery, SysPermissionMapper>{

    public SysPermissionRepository(SysPermissionMapper mapper) {
        super(mapper, E -> BeanUtils.createFrom(E, SysPermissionDTO.class));
    }

    @Override
    protected LineWrapper<SysPermissionQuery, LambdaQueryWrapper<SysPermission>> buildQueryWrapper(SysPermissionQuery query){
        return LineWrapper.ofLambdaQueryWrapper(SysPermission.class, query);
    }

}
