package com.smartticket.infra.redis;

/**
 * Redis key 统一定义。
 *
 * <p>所有 Redis key 在这里集中生成，避免业务代码分散拼接 key 字符串。</p>
 */
public final class RedisKeys {
    /**
     * 构造RedisKeys。
     */
    private RedisKeys() {
    }

    /**
     * 处理详情。
     */
    public static String ticketDetail(Long ticketId) {
        return "ticket:detail:" + ticketId;
    }

    /**
     * 处理幂等。
     */
    public static String ticketIdempotency(Long userId, String idempotencyKey) {
        return "ticket:idempotency:" + userId + ":" + idempotencyKey;
    }

    /**
     * 处理幂等锁。
     */
    public static String ticketIdempotencyLock(Long userId, String idempotencyKey) {
        return "ticket:idempotency:" + userId + ":" + idempotencyKey + ":lock";
    }

    /**
     * 处理会话。
     */
    public static String agentSession(String sessionId) {
        return "agent:session:" + sessionId;
    }

    /**
     * 处理工单领域记忆。
     */
    public static String agentTicketDomainMemory(Long ticketId) {
        return "agent:memory:ticket:" + ticketId;
    }
}
