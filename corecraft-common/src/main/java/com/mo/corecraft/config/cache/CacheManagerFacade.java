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
        return cache.get(generateCacheKey(cacheKey, params), cacheKey.getType());
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
            cache.put(generateCacheKey(cacheKey, params), value);
        }
    }

    public static <T> T getIfAbsent(CacheKey<T> cacheKey, Supplier<T> supplier, Object... params) {
        T value = get(cacheKey, params);
        if (value != null) return value;
        value = supplier.get();
        if (value == null) return null;
        set(cacheKey, value, params);
        return value;
    }

    public static void delete(CacheKey<?> cacheKey, Object... params) {
        Cache cache = getCacheManager().getCache(getCacheName(cacheKey));
        if (cache != null) {
            cache.evict(generateCacheKey(cacheKey, params));
        }
    }

    public static void clear(CacheKey<?> cacheKey) {
        Cache cache = getCacheManager().getCache(getCacheName(cacheKey));
        if (cache != null) {
            cache.clear();
        }
    }

    private static String getCacheName(CacheKey<?> cacheKey) {
        return cacheKey.getCacheName();
    }
}
