package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mo.corecraft.mapper.SysDictItemMapper;
import com.mo.corecraft.model.entity.SysDictItem;
import com.mo.corecraft.model.query.SysDictItemQuery;
import com.mo.corecraft.utils.LineWrapper;
import org.springframework.stereotype.Repository;

@Repository
public class SysDictItemRepository extends AbstractRepository<SysDictItem, SysDictItemQuery, SysDictItemMapper> {

    public SysDictItemRepository(SysDictItemMapper mapper) {
        super(mapper);
    }

    @Override
    protected LineWrapper<SysDictItemQuery, LambdaQueryWrapper<SysDictItem>> buildQueryWrapper(SysDictItemQuery query) {
        var wrapper = new LambdaQueryWrapper<SysDictItem>();
        return LineWrapper.ofLambdaQueryWrapper(query, wrapper)
                .ifNotNull(query.getId(), (w, id) -> w.eq(SysDictItem::getId, id))
                .ifNotNull(query.getDictTypeId(), (w, dictTypeId) -> w.eq(SysDictItem::getDictTypeId, dictTypeId))
                .ifNotBlank(query.getLabel(), (w, label) -> w.like(SysDictItem::getLabel, label))
                .ifNotBlank(query.getValue(), (w, value) -> w.eq(SysDictItem::getValue, value))
                .ifNotNull(query.getEnabled(), (w, enabled) -> w.eq(SysDictItem::isEnabled, enabled))
                .when(true, (w, q) -> w.orderByAsc(SysDictItem::getSort).orderByAsc(SysDictItem::getId));
    }

    public int deleteByDictTypeId(Long dictTypeId) {
        return mapper.delete(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictTypeId, dictTypeId));
    }
}
