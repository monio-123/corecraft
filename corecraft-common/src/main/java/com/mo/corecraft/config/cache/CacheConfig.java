package com.mo.corecraft.config.cache;

import com.mo.corecraft.utils.CacheHelper;
import com.mo.corecraft.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory factory,
                                          @Value("${spring.cache.redis.time-to-live:600000}") long ttlMillis) {
        // 复用 JsonUtils 序列化口径，在 Builder 上挂 MixIn 并显式开启 defaultTyping（Spring Data Redis 4 默认关闭）
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer
                .builder(JsonUtils::baseMapperBuilder)
                .customize(b -> b.addMixIn(SimpleGrantedAuthority.class, SimpleGrantedAuthorityMixin.class))
                .enableUnsafeDefaultTyping()
                .build();
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
                )
                .entryTtl(Duration.ofMillis(ttlMillis))
                .disableCachingNullValues();
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }


    @Bean("globalKeyGenerator")
    public KeyGenerator keyGenerator() {
        return (target, method, params) ->
                CacheHelper.buildCacheKey(target.getClass().getSimpleName(), method.getName(), params);
    }

}
