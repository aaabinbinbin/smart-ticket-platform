package com.smartticket.agent.service;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.infra.redis.RedisJsonClient;
import com.smartticket.infra.redis.RedisKeys;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * 智能体会话缓存服务。
 *
 * <p>负责把 {@link AgentSessionContext} 存入 Redis，供后续多轮对话阶段复用。
 * 本服务只管理对话上下文，不直接读写工单表。</p>
 */
@Service
public class AgentSessionCacheService {
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    // RedisJSON客户端
    private final RedisJsonClient redisJsonClient;

    /**
     * 构造智能体会话缓存服务。
     */
    public AgentSessionCacheService(RedisJsonClient redisJsonClient) {
        this.redisJsonClient = redisJsonClient;
    }

    /**
     * 获取详情。
     */
    public AgentSessionContext get(String sessionId) {
        AgentSessionContext context = redisJsonClient.get(RedisKeys.agentSession(sessionId), AgentSessionContext.class);
        return context == null ? AgentSessionContext.builder().build() : context;
    }

    /**
     * 处理保存。
     */
    public void save(String sessionId, AgentSessionContext context) {
        redisJsonClient.set(RedisKeys.agentSession(sessionId), context, SESSION_TTL);
    }

    /**
     * 清理数据。
     */
    public void clear(String sessionId) {
        redisJsonClient.delete(RedisKeys.agentSession(sessionId));
    }
}
