package com.smartticket.infra.redis;

/**
 * Central Redis key definitions.
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
