package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mo.corecraft.config.security.resource.SecurityUserRecord;
import com.mo.corecraft.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    SecurityUserRecord selectByUsername(String username);

//    List<SysUserDTO> selectUserList(@Param("query") SysUserQuery query);
//
//    List<Long> selectUserIds(@Param("query") SysUserQuery query);
//
//    Page<SysUserDTO> selectUserByIds(@Param("ids") List<Long> ids);
}
