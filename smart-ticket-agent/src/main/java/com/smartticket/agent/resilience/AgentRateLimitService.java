package com.smartticket.agent.resilience;

import com.smartticket.biz.model.CurrentUser;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 请求限流服务。
 *
 * <p>该服务位于 Agent 入口保护层，在加载 session 和执行 Tool 之前先按用户与全局维度做滑动窗口限流。
 * 它只返回是否允许进入主链，不执行写操作，也不会修改 session、memory、pendingAction 或 trace。</p>
 */
@Service
public class AgentRateLimitService {
    private final int maxRequestsPerUserPerMinute;
    private final int maxGlobalRequestsPerMinute;
    private final Clock clock;
    private final Map<Long, Deque<Long>> userWindows = new ConcurrentHashMap<>();
    private final Deque<Long> globalWindow = new ArrayDeque<>();

    @Autowired
    public AgentRateLimitService(
            @Value("${smart-ticket.agent.rate-limit.user-per-minute:60}") int maxRequestsPerUserPerMinute,
            @Value("${smart-ticket.agent.rate-limit.global-per-minute:600}") int maxGlobalRequestsPerMinute
    ) {
        this(maxRequestsPerUserPerMinute, maxGlobalRequestsPerMinute, Clock.systemUTC());
    }

    AgentRateLimitService(int maxRequestsPerUserPerMinute, int maxGlobalRequestsPerMinute, Clock clock) {
        this.maxRequestsPerUserPerMinute = Math.max(maxRequestsPerUserPerMinute, 1);
        this.maxGlobalRequestsPerMinute = Math.max(maxGlobalRequestsPerMinute, 1);
        this.clock = clock;
    }

    /**
     * 尝试登记一次请求。
     *
     * @param currentUser 当前登录用户
     * @param sessionId 会话 ID，仅用于调用语义说明，当前限流按用户和全局维度执行
     * @return true 表示允许进入 Agent 主链；false 表示触发限流
     */
    public boolean tryAcquire(CurrentUser currentUser, String sessionId) {
        long now = clock.millis();
        synchronized (globalWindow) {
            evictExpired(globalWindow, now);
            if (globalWindow.size() >= maxGlobalRequestsPerMinute) {
                return false;
            }
            globalWindow.addLast(now);
        }

        long userId = currentUser == null || currentUser.getUserId() == null ? -1L : currentUser.getUserId();
        Deque<Long> userWindow = userWindows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (userWindow) {
            evictExpired(userWindow, now);
            if (userWindow.size() >= maxRequestsPerUserPerMinute) {
                // 用户维度拒绝时回滚刚才的全局登记，避免被拒请求占用全局容量。
                synchronized (globalWindow) {
                    globalWindow.removeLastOccurrence(now);
                }
                return false;
            }
            userWindow.addLast(now);
            return true;
        }
    }

    private void evictExpired(Deque<Long> window, long now) {
        long lowerBound = now - 60_000L;
        while (!window.isEmpty() && window.peekFirst() < lowerBound) {
            window.removeFirst();
        }
    }
}
