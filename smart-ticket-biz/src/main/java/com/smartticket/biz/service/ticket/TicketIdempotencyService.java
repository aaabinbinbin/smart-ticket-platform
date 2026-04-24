package com.smartticket.biz.service.ticket;

import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * 工单创建幂等服务。
 *
 * <p>基于 Redis 保存创建结果和短期处理锁，用于防止同一用户使用相同幂等键重复创建工单。</p>
 */
@Service
public class TicketIdempotencyService {
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    // RedisJSON客户端
    private final RedisJsonClient redisJsonClient;

    /**
     * 构造工单幂等服务。
     */
    public TicketIdempotencyService(RedisJsonClient redisJsonClient) {
        this.redisJsonClient = redisJsonClient;
    }

    /**
     * 处理启用。
     */
    public boolean enabled(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }

    /**
     * 规范化处理。
     */
    public String normalize(String idempotencyKey) {
        return idempotencyKey == null ? null : idempotencyKey.trim();
    }

    /**
     * 获取创建工单ID。
     */
    public Long getCreatedTicketId(Long userId, String idempotencyKey) {
        return redisJsonClient.get(RedisKeys.ticketIdempotency(userId, idempotencyKey), Long.class);
    }

    /**
     * 获取创建Lock。
     */
    public boolean acquireCreateLock(Long userId, String idempotencyKey) {
        Boolean result = redisJsonClient.setIfAbsent(
                RedisKeys.ticketIdempotencyLock(userId, idempotencyKey),
                "1",
                LOCK_TTL
        );
        return Boolean.TRUE.equals(result);
    }

    /**
     * 保存创建工单ID。
     */
    public void saveCreatedTicketId(Long userId, String idempotencyKey, Long ticketId) {
        redisJsonClient.set(RedisKeys.ticketIdempotency(userId, idempotencyKey), ticketId, RESULT_TTL);
    }

    /**
     * 释放创建Lock。
     */
    public void releaseCreateLock(Long userId, String idempotencyKey) {
        redisJsonClient.delete(RedisKeys.ticketIdempotencyLock(userId, idempotencyKey));
    }
}

