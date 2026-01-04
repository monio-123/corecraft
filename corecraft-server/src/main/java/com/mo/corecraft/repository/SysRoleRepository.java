package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.mapper.SysRoleMapper;
import com.mo.corecraft.model.dto.SysRoleDTO;
import com.mo.corecraft.model.entity.SysRole;
import com.mo.corecraft.model.query.SysRoleQuery;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.LineWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SysRoleRepository {

    private final SysRoleMapper sysRoleMapper;

    public SysRoleDTO selectUser(SysRoleQuery query){
        SysRole sysRole = sysRoleMapper.selectOne(buildQueryWrapper(query).build());
        Optional.ofNullable(sysRole).orElseThrow(() -> new CoreCraftException(ResultCodeEnum.NOT_FOUND, "角色不存在!"));
        return BeanUtils.createFrom(sysRole, SysRoleDTO.class);
    }

    public List<SysRoleDTO> selectUserList(SysRoleQuery query){
        List<SysRole> sysRoles = sysRoleMapper.selectList(buildQueryWrapper(query).build());
        return sysRoles.stream().map(sysRole -> BeanUtils.createFrom(sysRole, SysRoleDTO.class)).toList();
    }

    public IPage<SysRoleDTO> selectUserPage(Page<SysRole> page, SysRoleQuery query){
        Page<SysRole> sysRolePage = sysRoleMapper.selectPage(page, buildQueryWrapper(query).build());
        return sysRolePage.convert(rolePage -> BeanUtils.createFrom(rolePage, SysRoleDTO.class));
    }

    private LineWrapper<SysRoleQuery, LambdaQueryWrapper<SysRole>> buildQueryWrapper(SysRoleQuery query){
        return LineWrapper.ofLambdaQueryWrapper(SysRole.class, query);
    }

}
