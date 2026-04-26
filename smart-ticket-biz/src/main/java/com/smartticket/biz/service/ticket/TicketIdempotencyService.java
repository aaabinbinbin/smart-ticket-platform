package com.smartticket.biz.service.ticket;

import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 工单创建幂等服务。
 *
 * <p>基于 Redis 保存创建结果和短期处理锁。Redis 不可用时降级为无幂等保护，
 * 允许创建工单但可能出现重复提交，优先保证业务可用性。</p>
 */
@Service
public class TicketIdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(TicketIdempotencyService.class);
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(120);

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

    /**
     * 获取已创建的工单 ID。Redis 不可用时返回 null，走正常创建流程。
     */
    public Long getCreatedTicketId(Long userId, String idempotencyKey) {
        try {
            return redisJsonClient.get(RedisKeys.ticketIdempotency(userId, idempotencyKey), Long.class);
        } catch (RuntimeException ex) {
            log.warn("Redis 不可用，跳过幂等结果查询: userId={}, key={}", userId, idempotencyKey);
            return null;
        }
    }

    /**
     * 获取创建锁。Redis 不可用时返回 true，允许创建但无锁保护。
     */
    public boolean acquireCreateLock(Long userId, String idempotencyKey) {
        try {
            Boolean result = redisJsonClient.setIfAbsent(
                    RedisKeys.ticketIdempotencyLock(userId, idempotencyKey),
                    "1",
                    LOCK_TTL
            );
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException ex) {
            log.warn("Redis 不可用，跳过幂等锁: userId={}, key={}", userId, idempotencyKey);
            return true;
        }
    }

    public void saveCreatedTicketId(Long userId, String idempotencyKey, Long ticketId) {
        try {
            redisJsonClient.set(RedisKeys.ticketIdempotency(userId, idempotencyKey), ticketId, RESULT_TTL);
        } catch (RuntimeException ex) {
            log.warn("Redis 不可用，跳过幂等结果保存: userId={}, key={}", userId, idempotencyKey);
        }
    }

    public void releaseCreateLock(Long userId, String idempotencyKey) {
        try {
            redisJsonClient.delete(RedisKeys.ticketIdempotencyLock(userId, idempotencyKey));
        } catch (RuntimeException ex) {
            log.warn("Redis 不可用，跳过幂等锁释放: userId={}, key={}", userId, idempotencyKey);
        }
    }
}

