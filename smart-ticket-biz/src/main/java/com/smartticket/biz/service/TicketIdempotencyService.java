package com.smartticket.biz.service;

import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Redis based idempotency support for ticket creation.
 */
@Service
public class TicketIdempotencyService {
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final RedisJsonClient redisJsonClient;

    public TicketIdempotencyService(RedisJsonClient redisJsonClient) {
        this.redisJsonClient = redisJsonClient;
    }

    public boolean enabled(String idempotencyKey) {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }

    public String normalize(String idempotencyKey) {
        return idempotencyKey == null ? null : idempotencyKey.trim();
    }

    public Long getCreatedTicketId(Long userId, String idempotencyKey) {
        return redisJsonClient.get(RedisKeys.ticketIdempotency(userId, idempotencyKey), Long.class);
    }

    public boolean acquireCreateLock(Long userId, String idempotencyKey) {
        Boolean result = redisJsonClient.setIfAbsent(
                RedisKeys.ticketIdempotencyLock(userId, idempotencyKey),
                "1",
                LOCK_TTL
        );
        return Boolean.TRUE.equals(result);
    }

    public void saveCreatedTicketId(Long userId, String idempotencyKey, Long ticketId) {
        redisJsonClient.set(RedisKeys.ticketIdempotency(userId, idempotencyKey), ticketId, RESULT_TTL);
    }

    public void releaseCreateLock(Long userId, String idempotencyKey) {
        redisJsonClient.delete(RedisKeys.ticketIdempotencyLock(userId, idempotencyKey));
    }
}
