package com.smartticket.agent.service;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Redis storage for Agent session context.
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
