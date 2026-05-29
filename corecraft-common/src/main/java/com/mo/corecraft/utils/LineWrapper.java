package com.mo.corecraft.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class LineWrapper<T, R> {

    private final T param;
    private final R wrapper;

    private LineWrapper(T param, R wrapper) {
        this.param = param;
        this.wrapper = wrapper;
    }

    public static <T, V> LineWrapper<T, LambdaQueryWrapper<V>> ofLambdaQueryWrapper(Class<V> clazz, T param){
        return new LineWrapper<>(param, new LambdaQueryWrapper<>());
    }

    public static <T, V> LineWrapper<T, LambdaQueryWrapper<V>> ofLambdaQueryWrapper(T param, LambdaQueryWrapper<V> wrapper){
        return new LineWrapper<>(param, wrapper);
    }

    public R build() {
        return wrapper;
    }

    public <V> LineWrapper<T, R> ifNotNull(V value, BiConsumer<R, V> consumer) {
        if (value != null) {
            consumer.accept(wrapper, value);
        }
        return this;
    }

    public <V> LineWrapper<T, R> ifNotEmpty(V value, BiConsumer<R, V> consumer) {
        if (value instanceof java.util.Collection<?> collection) {
            if (!collection.isEmpty()) {
                consumer.accept(wrapper, value);
            }
        }
        return this;
    }

    public <V> LineWrapper<T, R> ifNotBlank(V value, BiConsumer<R, V> consumer) {
        if (value instanceof String && StringUtils.isNotBlank((String) value)) {
            consumer.accept(wrapper, value);
        }
        return this;
    }

    public LineWrapper<T, R> when(Predicate<T> predicate, BiConsumer<R, T> consumer) {
        if (predicate.test(param)) {
            consumer.accept(wrapper, param);
        }
        return this;
    }

    public LineWrapper<T, R> when(boolean condition, BiConsumer<R, T> consumer) {
        if (condition) {
            consumer.accept(wrapper, param);
        }
        return this;
    }
}
