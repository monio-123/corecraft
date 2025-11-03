package com.mo.corecraft.config.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CacheKey<T> {

    private final String cacheName;
    private final String className;
    private final String methodName;
    private final Class<T> type;               // 直接用 Class
    private final TypeReference<T> typeReference; // 用于复杂泛型

    private CacheKey(String cacheName, String className, String methodName, Class<T> type, TypeReference<T> typeReference) {
        this.cacheName = cacheName;
        this.className = className;
        this.methodName = methodName;
        this.type = type;
        this.typeReference = typeReference;
    }

    /** 构造方法：Class 类型 */
    public static <T> CacheKey<T> of(String cacheName, String className, String methodName, Class<T> type) {
        return new CacheKey<>(cacheName, className, methodName, type, null);
    }

    /** 构造方法：TypeReference 类型 */
    public static <T> CacheKey<T> of(String cacheName, String className, String methodName, TypeReference<T> typeReference) {
        return new CacheKey<>(cacheName, className, methodName, null, typeReference);
    }

    public static <T> CacheKey<T> of(String cacheName, Class<T> type) {
        return of(cacheName, type.getName(), "", type);
    }

}
