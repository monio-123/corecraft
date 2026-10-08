package com.mo.corecraft.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.config.security.resource.SecurityUserRecord;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.model.resp.SysUserResp;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    SecurityUserRecord selectByUsername(String username);

    SysUserResp selectOne(@Param("query")SysUserQuery query);

    List<SysUserResp> selectList(@Param("query")SysUserQuery query);

    Page<SysUserResp> selectPage(@Param("page")Page<?> page ,@Param("query")SysUserQuery query);
}
