package com.smartticket.infra.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis JSON 访问适配器。
 *
 * <p>封装字符串 Redis 模板和 Jackson 序列化逻辑，供业务模块读写短期缓存和上下文数据。</p>
 */
@Component
public class RedisJsonClient {
    // stringRedis模板
    private final StringRedisTemplate stringRedisTemplate;
    // object映射接口
    private final ObjectMapper objectMapper;

    /**
     * 构造RedisJSON客户端。
     */
    public RedisJsonClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public <T> T get(String key, Class<T> targetType) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, targetType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize redis value, key=" + key, ex);
        }
    }

    /**
     * 设置数据。
     */
    public void set(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize redis value, key=" + key, ex);
        }
    }

    /**
     * 按不存在时处理。
     */
    public Boolean setIfAbsent(String key, String value, Duration ttl) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl);
    }

    /**
     * 处理delete。
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }
}
