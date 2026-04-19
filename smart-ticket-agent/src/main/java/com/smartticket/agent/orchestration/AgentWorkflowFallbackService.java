package com.smartticket.agent.orchestration;

import com.smartticket.agent.llm.service.LlmAgentService;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.service.IntentRouter;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRegistry;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import org.springframework.stereotype.Service;

/**
 * 阶段八工作流 fallback 构造服务。
 *
 * <p>该服务集中保留“规则路由 + LLM 意图增强 + 规则参数 + LLM 参数增强”的旧链路，
 * 让阶段九编排器不需要关心阶段八内部细节。</p>
 */
@Service
public class AgentWorkflowFallbackService {
    /**
     * 规则版意图路由器。
     */
    private final IntentRouter intentRouter;

    /**
     * 规则版参数抽取器。
     */
    private final AgentToolParameterExtractor parameterExtractor;

    /**
     * Tool 注册表，用于根据 fallback intent 找到默认 Tool。
     */
    private final AgentToolRegistry toolRegistry;

    /**
     * 阶段八 LLM 能力服务。
     */
    private final LlmAgentService llmAgentService;

    public AgentWorkflowFallbackService(
            IntentRouter intentRouter,
            AgentToolParameterExtractor parameterExtractor,
            AgentToolRegistry toolRegistry,
            LlmAgentService llmAgentService
    ) {
        this.intentRouter = intentRouter;
        this.parameterExtractor = parameterExtractor;
        this.toolRegistry = toolRegistry;
        this.llmAgentService = llmAgentService;
    }

    /**
     * 构造阶段八 fallback。
     */
    public AgentWorkflowFallback build(String message, AgentSessionContext context) {
        IntentRoute route = buildRoute(message, context);
        ToolCallPlan plan = buildPlan(message, context, route);
        return AgentWorkflowFallback.builder()
                .route(route)
                .plan(plan)
                .build();
    }

    /**
     * 先执行规则路由，再让 LLM 尝试增强意图。
     */
    private IntentRoute buildRoute(String message, AgentSessionContext context) {
        IntentRoute fallbackRoute = intentRouter.route(message, context);
        return llmAgentService.routeOrFallback(message, context, fallbackRoute);
    }

    /**
     * 基于 route 构造 fallback ToolCallPlan。
     */
    private ToolCallPlan buildPlan(String message, AgentSessionContext context, IntentRoute route) {
        AgentTool tool = toolRegistry.requireByIntent(route.getIntent());
        AgentToolParameters fallbackParameters = parameterExtractor.extract(message, context);
        AgentToolParameters parameters = llmAgentService.extractParametersOrFallback(
                message, context, route, fallbackParameters);
        return ToolCallPlan.builder()
                .intent(route.getIntent())
                .toolName(tool.name())
                .parameters(parameters)
                .llmGenerated(false)
                .reason("阶段八 fallback 工作流")
                .build();
    }
}
