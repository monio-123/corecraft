package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysRoleMapper;
import com.mo.corecraft.model.dto.SysRoleDTO;
import com.mo.corecraft.model.entity.SysRole;
import com.mo.corecraft.model.query.SysRoleQuery;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

@Repository
public class SysRoleRepository extends AbstractRepository<SysRole, SysRoleDTO, SysRoleQuery, SysRoleMapper>{

    public SysRoleRepository(SysRoleMapper mapper) {
        super(mapper, E -> BeanUtils.createFrom(E, SysRoleDTO.class));
    }

    @Override
    protected LineWrapper<SysRoleQuery, LambdaQueryWrapper<SysRole>> buildQueryWrapper(SysRoleQuery query){
        return LineWrapper.ofLambdaQueryWrapper(SysRole.class, query);
    }

}
