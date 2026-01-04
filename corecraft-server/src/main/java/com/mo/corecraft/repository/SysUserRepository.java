package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.model.dto.SysUserDTO;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.mapper.SysUserMapper;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.utils.BeanUtils;
import com.mo.corecraft.utils.LineWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SysUserRepository {

    private final SysUserMapper sysUserMapper;

    public SysUserDTO selectUser(SysUserQuery query){
        SysUser sysUser = sysUserMapper.selectOne(buildQueryWrapper(query).build());
        Optional.ofNullable(sysUser).orElseThrow(() -> new CoreCraftException(ResultCodeEnum.NOT_FOUND, "用户不存在!"));
        return BeanUtils.createFrom(sysUser, SysUserDTO.class);
    }

    public List<SysUserDTO> selectUserList(SysUserQuery query){
        List<SysUser> sysUsers = sysUserMapper.selectList(buildQueryWrapper(query).build());
        return sysUsers.stream().map(sysUser -> BeanUtils.createFrom(sysUser, SysUserDTO.class)).toList();
    }

    public IPage<SysUserDTO> selectUserPage(Page<SysUser> page, SysUserQuery query){
        Page<SysUser> sysUserPage = sysUserMapper.selectPage(page, buildQueryWrapper(query).build());
        return sysUserPage.convert(sysUser -> BeanUtils.createFrom(sysUser, SysUserDTO.class));
    }

    private LineWrapper<SysUserQuery, LambdaQueryWrapper<SysUser>> buildQueryWrapper(SysUserQuery query){
        return LineWrapper.ofLambdaQueryWrapper(SysUser.class, query)
                .ifNotBlank(query.getUsername(), (wrapper, username) -> wrapper.like(SysUser::getUsername, username))
                .ifNotBlank(query.getPermissionCode(), (wrapper, permissionCode) -> wrapper.like(SysUser::getUsername, permissionCode));
    }

}
