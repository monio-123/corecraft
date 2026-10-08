package com.mo.corecraft.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mo.corecraft.enums.ResultCodeEnum;
import com.mo.corecraft.exception.CoreCraftException;
import com.mo.corecraft.utils.LineWrapper;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

@RequiredArgsConstructor
public abstract class AbstractRepository<E, Q, M extends BaseMapper<E>> {

    protected final M mapper;

    protected abstract LineWrapper<Q, LambdaQueryWrapper<E>> buildQueryWrapper(Q query);

    protected LambdaQueryWrapper<E> wrapper(Q query) {
        return buildQueryWrapper(query).build();
    }

    public <D> Optional<D> find(Q query, Function<E, D> converter) {
        return Optional.ofNullable(mapper.selectOne(wrapper(query)))
                .map(converter);
    }

    public <D> D get(Q query, Function<E, D> converter) {
        return find(query, converter)
                .orElseThrow(() -> new CoreCraftException(ResultCodeEnum.NOT_FOUND, "数据不存在"));
    }

    public <D> List<D> list(Q query, Function<E, D> converter) {
        return mapper.selectList(wrapper(query))
                .stream()
                .map(converter)
                .toList();
    }

    public <D> IPage<D> page(Page<E> page, Q query, Function<E, D> converter) {
        return mapper.selectPage(page, wrapper(query))
                .convert(converter);
    }

    public int insert(E entity) {
        return mapper.insert(entity);
    }

    public int update(E entity) {
        return mapper.updateById(entity);
    }

    public <P> int update(Long id, P p, BiConsumer<P, E> consumer) {
        E entity = Optional.ofNullable(mapper.selectById(id))
                .orElseThrow(() -> new CoreCraftException(ResultCodeEnum.NOT_FOUND));
        consumer.accept(p, entity);
        return update(entity);
    }

    public int delete(Long id) {
        return mapper.deleteById(id);
    }

    public int deleteBatch(List<Long> ids) {
        return mapper.deleteBatchIds(ids);
    }
}
