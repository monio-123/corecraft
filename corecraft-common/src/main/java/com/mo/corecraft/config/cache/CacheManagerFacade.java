package com.mo.corecraft.config.cache;

import com.mo.corecraft.utils.CacheHelper;
import com.mo.corecraft.utils.SpringContextHolder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.function.Supplier;

public final class CacheManagerFacade {

    private static volatile CacheManager cacheManager;

    private static CacheManager getCacheManager() {
        if (cacheManager == null) {
            synchronized (CacheManagerFacade.class) {
                if (cacheManager == null) {
                    cacheManager = SpringContextHolder.getBean(CacheManager.class);
                }
            }
        }
        return cacheManager;
    }

    private CacheManagerFacade() {}

    /** 核心 key 生成方法，支持对象参数，和 globalKeyGenerator 保持一致 */
    public static String generateCacheKey(CacheKey<?> cacheKey, Object... params) {
        return CacheHelper.buildCacheKey(cacheKey.getClassName(), cacheKey.getMethodName(), params);
    }

    public static <T> T get(CacheKey<T> cacheKey, String key) {
        Cache cache = getCacheManager().getCache(getCacheName(cacheKey));
        if (cache == null) return null;
        return cache.get(key, cacheKey.getType());
    }

    public static <T> T get(CacheKey<T> cacheKey, Object... params) {
        Cache cache = getCacheManager().getCache(getCacheName(cacheKey));
        if (cache == null) return null;
        return cache.get(generateCacheKey(cacheKey,  params), cacheKey.getType());
    }

    public static <T> void set(CacheKey<T> cacheKey, String key, T value) {
        Cache cache = getCacheManager().getCache(getCacheName(cacheKey));
        if (cache != null) {
            cache.put(key, value);
        }
    }

    public static <T> void set(CacheKey<T> cacheKey, T value, Object... params) {
        Cache cache = getCacheManager().getCache(getCacheName(cacheKey));
        if (cache != null) {
            cache.put(generateCacheKey(cacheKey,  params), value);
        }
    }

    public static <T> T getIfAbsent(CacheKey<T> cacheKey, Supplier<T> supplier, Object... params) {
        T value = get(cacheKey, params);
        if (value != null) return value;
        value = supplier.get();
        set(cacheKey, value, params);
        return value;
    }

    public static void delete(CacheKey<?> cacheKey, Object... params) {
        Cache cache = getCacheManager().getCache(getCacheName(cacheKey));
        if (cache != null) {
            cache.evict(generateCacheKey(cacheKey, params));
        }
    }

    /** 默认缓存名处理 */
    private static String getCacheName(CacheKey<?> cacheKey) {
        // 简单方案：取 key 前缀，也可以在 CacheKey 中单独定义 cacheName
        return cacheKey.getCacheName();
    }
}
