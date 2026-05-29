package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysDictTypeMapper;
import com.mo.corecraft.model.entity.SysDictType;
import com.mo.corecraft.model.query.SysDictTypeQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

@Repository
public class SysDictTypeRepository extends AbstractRepository<SysDictType, SysDictTypeQuery, SysDictTypeMapper> {

    public SysDictTypeRepository(SysDictTypeMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<SysDictTypeQuery, LambdaQueryWrapper<SysDictType>> buildQueryWrapper(SysDictTypeQuery query) {
        var wrapper = new LambdaQueryWrapper<SysDictType>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(SysDictType::getId, id))
                .ifNotBlank(query.getCode(), (w, code) -> w.eq(SysDictType::getCode, code))
                .ifNotBlank(query.getName(), (w, name) -> w.like(SysDictType::getName, name))
                .ifNotNull(query.getEnabled(), (w, enabled) -> w.eq(SysDictType::isEnabled, enabled))
                .when(true, (w, q) -> w.orderByAsc(SysDictType::getId));
    }
}
