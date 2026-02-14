package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.model.dto.SysUserDTO;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.mapper.SysUserMapper;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

@Repository
public class SysUserRepository extends AbstractRepository<SysUser, SysUserDTO, SysUserQuery, SysUserMapper>{

    public SysUserRepository(SysUserMapper mapper) {
        super(mapper, E -> BeanUtils.createFrom(E, SysUserDTO.class));
    }

    @Override
    protected LineWrapper<SysUserQuery, LambdaQueryWrapper<SysUser>> buildQueryWrapper(SysUserQuery query){
        return LineWrapper.ofLambdaQueryWrapper(SysUser.class, query)
                .ifNotBlank(query.getUsername(), (wrapper, username) -> wrapper.like(SysUser::getUsername, username))
                .ifNotBlank(query.getPermissionCode(), (wrapper, permissionCode) -> wrapper.like(SysUser::getUsername, permissionCode));
    }

}
