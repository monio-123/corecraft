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
import java.util.function.Function;

@RequiredArgsConstructor
public abstract class AbstractRepository<E, D, Q, M extends BaseMapper<E>> {

    protected final M mapper;

    private final Function<E, D> converter;

    protected abstract LineWrapper<Q, LambdaQueryWrapper<E>> buildQueryWrapper(Q query);

    protected LambdaQueryWrapper<E> wrapper(Q query) {
        return buildQueryWrapper(query).build();
    }

    protected D toDTO(E entity) {
        return converter.apply(entity);
    }

    public Optional<D> find(Q query) {
        return Optional.ofNullable(mapper.selectOne(wrapper(query)))
                .map(this::toDTO);
    }

    public D get(Q query) {
        return find(query)
                .orElseThrow(() -> new CoreCraftException(ResultCodeEnum.NOT_FOUND, "数据不存在"));
    }

    public List<D> list(Q query) {
        return mapper.selectList(wrapper(query))
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public IPage<D> page(Page<E> page, Q query) {
        return mapper.selectPage(page, wrapper(query))
                .convert(this::toDTO);
    }

    public int insert(E entity) {
        return mapper.insert(entity);
    }

    public int update(E entity) {
        return mapper.updateById(entity);
    }
}
