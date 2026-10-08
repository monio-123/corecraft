package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.model.entity.SysUser;
import com.mo.corecraft.mapper.SysUserMapper;
import com.mo.corecraft.model.query.SysUserQuery;
import com.mo.corecraft.model.resp.SysUserResp;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SysUserRepository extends AbstractRepository<SysUser, SysUserQuery, SysUserMapper>{

    public SysUserRepository(SysUserMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<SysUserQuery, LambdaQueryWrapper<SysUser>> buildQueryWrapper(SysUserQuery query){
        return LineWrapper.ofLambdaQueryWrapper(SysUser.class, query)
                .ifNotNull(query.getId(), (wrapper, id) -> wrapper.eq(SysUser::getId, id))
                .ifNotBlank(query.getUsername(), (wrapper, username) -> wrapper.like(SysUser::getUsername, username));
    }

    public SysUserResp selectOne(SysUserQuery query) {
        return mapper.selectOne(query);
    }

    public List<SysUserResp> selectList(SysUserQuery query) {
        return mapper.selectList(query);
    }

    public Page<SysUserResp> selectPage(Page<?> page, SysUserQuery query) {
        return mapper.selectPage(page, query);
    }

    public SysUser entityById(Long id) {
        return mapper.selectById(id);
    }

}
