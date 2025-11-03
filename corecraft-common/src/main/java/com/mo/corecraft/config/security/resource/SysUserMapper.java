package com.mo.corecraft.config.security.resource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.DO.SysUser;
import com.mo.corecraft.DTO.SysUserDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    SysUserDTO selectByUsername(String username);
}
