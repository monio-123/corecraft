package com.mo.corecraft.config.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
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
        // 1.objectMapper 需要传给GenericJackson2JsonRedisSerializer 使得系统内与Redis序列化器的序列化能力一致
        // 2.objectMapper 需要开启默认的类型序列化 否则缓存数据无法反序列化
        // 3.bean是单例 这里不能直接传入全局的objectMapper
        ObjectMapper objectMapper = JsonUtils.initializeBaseMapper();
        // 默认无法反序列化 SimpleGrantedAuthority 因为SimpleGrantedAuthority没有无参构造函数或者@JsonCreator或@JsonProperty("authority")
        objectMapper.addMixIn(SimpleGrantedAuthority.class, SimpleGrantedAuthorityMixin.class);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper))
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
