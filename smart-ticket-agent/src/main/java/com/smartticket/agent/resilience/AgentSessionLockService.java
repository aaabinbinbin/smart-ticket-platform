package com.smartticket.agent.resilience;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/**
 * Agent session 级互斥锁服务。
 *
 * <p>该服务位于 Agent 入口保护层，用同一 sessionId 的非阻塞锁避免多轮 pendingAction、
 * activeTicketId 和 recentMessages 并发串写。当前实现是单实例内存锁；后续如需多实例部署，
 * 可以在不改变调用方契约的前提下替换为 Redis lock。它不执行写操作，也不会修改
 * session、memory、pendingAction 或 trace。</p>
 */
@Service
public class AgentSessionLockService {
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 尝试获取当前 session 的互斥锁。
     *
     * @param sessionId 会话 ID
     * @return true 表示本请求可以进入主链；false 表示已有同 session 请求正在处理
     */
    public boolean tryLock(String sessionId) {
        String key = normalize(sessionId);
        // 使用 tryLock 是为了在高压下快速失败，避免同一会话请求排队后继续写入过期上下文。
        return locks.computeIfAbsent(key, ignored -> new ReentrantLock()).tryLock();
    }

    /**
     * 释放当前 session 的互斥锁。
     *
     * @param sessionId 会话 ID
     */
    public void unlock(String sessionId) {
        String key = normalize(sessionId);
        ReentrantLock lock = locks.get(key);
        if (lock == null || !lock.isHeldByCurrentThread()) {
            return;
        }
        lock.unlock();
        if (!lock.isLocked() && !lock.hasQueuedThreads()) {
            locks.remove(key, lock);
        }
    }

    private String normalize(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "__anonymous_session__" : sessionId.trim();
    }
}
