package com.smartticket.agent.service;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Agent 会话缓存服务。
 *
 * <p>负责把 {@link AgentSessionContext} 存入 Redis，供后续多轮对话阶段复用。
 * 本服务只管理对话上下文，不直接读写工单表。</p>
 */
@Service
public class AgentSessionCacheService {
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final RedisJsonClient redisJsonClient;

    public AgentSessionCacheService(RedisJsonClient redisJsonClient) {
        this.redisJsonClient = redisJsonClient;
    }

    public AgentSessionContext get(String sessionId) {
        AgentSessionContext context = redisJsonClient.get(RedisKeys.agentSession(sessionId), AgentSessionContext.class);
        return context == null ? AgentSessionContext.builder().build() : context;
    }

    public void save(String sessionId, AgentSessionContext context) {
        redisJsonClient.set(RedisKeys.agentSession(sessionId), context, SESSION_TTL);
    }

    public void clear(String sessionId) {
        redisJsonClient.delete(RedisKeys.agentSession(sessionId));
    }
}
