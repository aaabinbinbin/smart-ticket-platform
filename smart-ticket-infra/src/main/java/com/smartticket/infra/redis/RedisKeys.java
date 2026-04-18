package com.smartticket.infra.redis;

/**
 * Redis key 统一定义。
 *
 * <p>所有 Redis key 在这里集中生成，避免业务代码分散拼接 key 字符串。</p>
 */
public final class RedisKeys {
    private RedisKeys() {
    }

    public static String ticketDetail(Long ticketId) {
        return "ticket:detail:" + ticketId;
    }

    public static String ticketIdempotency(Long userId, String idempotencyKey) {
        return "ticket:idempotency:" + userId + ":" + idempotencyKey;
    }

    public static String ticketIdempotencyLock(Long userId, String idempotencyKey) {
        return "ticket:idempotency:" + userId + ":" + idempotencyKey + ":lock";
    }

    public static String agentSession(String sessionId) {
        return "agent:session:" + sessionId;
    }
}
