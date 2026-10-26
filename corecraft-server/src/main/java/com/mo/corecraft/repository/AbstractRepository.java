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

    /**
     * 只写要改的那几列。
     *
     * **不能把查出来的整行丢进来**：实体里的 updateTime 是从库里读出来的（非 null），
     * MyBatis-Plus 默认把非 null 字段全写进 SET，SQL 于是变成 SET ..., update_time='旧值'，
     * 显式赋了值，DDL 的 ON UPDATE CURRENT_TIMESTAMP 就不再触发 —— 时间戳永远停在旧值。
     * 传一个只带 id + 改动列的"补丁实体"才是对的（null 列不进 SET）。
     *
     * <p>⚠️ **这条规则只存这一处**（原来 `SysConfigRepository` / `SysConfigNodeRepository`
     * 各抄了一份一模一样的注释，两个数字还各说各话）。
     */
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
