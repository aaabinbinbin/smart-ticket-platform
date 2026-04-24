package com.smartticket.agent.service;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentContextReferenceResolver;
import com.smartticket.agent.orchestration.AgentContextUpdater;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import org.springframework.stereotype.Service;

/**
 * 智能体会话上下文服务。
 *
 * <p>该服务统一管理短会话上下文的加载、保存、更新和最小指代消解。
 * 底层 Redis key 为 {@code agent:session:{sessionId}}，实际读写委托
 * {@link AgentSessionCacheService}。第一版只维护短上下文，不做长期记忆。</p>
 */
@Service
public class AgentSessionService {
    /**
     * Redis 会话缓存服务。
     */
    private final AgentSessionCacheService cacheService;

    /**
     * 上下文更新器。
     */
    private final AgentContextUpdater contextUpdater;

    /**
     * 最小指代消解器。
     */
    private final AgentContextReferenceResolver referenceResolver;

    /**
     * 构造智能体会话服务。
     */
    public AgentSessionService(
            AgentSessionCacheService cacheService,
            AgentContextUpdater contextUpdater,
            AgentContextReferenceResolver referenceResolver
    ) {
        this.cacheService = cacheService;
        this.contextUpdater = contextUpdater;
        this.referenceResolver = referenceResolver;
    }

    /**
     * 调用前加载会话上下文。
     *
     * @param sessionId 会话 ID
     * @return Redis 中已有上下文；不存在时返回空上下文对象
     */
    public AgentSessionContext load(String sessionId) {
        return cacheService.get(sessionId);
    }

    /**
     * 保存会话上下文。
     *
     * @param sessionId 会话 ID
     * @param context 待保存上下文
     */
    public void save(String sessionId, AgentSessionContext context) {
        cacheService.save(sessionId, context);
    }

    /**
     * 调用后根据 Tool 结果更新上下文。
     *
     * <p>P2 开始由 AgentFacade 在记忆写入完成后统一保存 session，避免同一轮先更新上下文再保存、
     * 然后又因 memory 回写再次保存。该方法因此只负责修改 context 本身，不直接提交。</p>
     *
     * @param sessionId 会话 ID
     * @param context 当前会话上下文
     * @param route 本轮意图路由
     * @param message 用户原始消息
     * @param toolResult Tool 执行结果
     */
    public void updateAfterTool(
            String sessionId,
            AgentSessionContext context,
            IntentRoute route,
            String message,
            AgentToolResult toolResult
    ) {
        contextUpdater.apply(context, route, message, toolResult);
    }

    /**
     * 基于短上下文执行最小指代消解。
     *
     * @param message 用户原始消息
     * @param context 当前会话上下文
     * @param parameters 待补齐的 Tool 参数
     */
    public void resolveReferences(String message, AgentSessionContext context, AgentToolParameters parameters) {
        referenceResolver.applyReferences(message, context, parameters);
    }

    /**
     * 清理会话上下文。
     *
     * @param sessionId 会话 ID
     */
    public void clear(String sessionId) {
        cacheService.clear(sessionId);
    }
}
