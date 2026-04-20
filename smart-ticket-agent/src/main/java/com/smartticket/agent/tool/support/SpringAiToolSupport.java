package com.smartticket.agent.tool.support;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.service.AgentSessionService;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

/**
 * Spring AI Tool Calling 适配支持类。
 *
 * <p>该类把 Spring AI 原生 Tool 调用重新收敛到项目已有的 AgentTool、Guard 和
 * biz/rag 服务调用链路中。模型只负责选择和填参，是否允许执行仍由代码侧校验决定。</p>
 */
@Component
public class SpringAiToolSupport {
    /**
     * toolContext 中保存当前用户的 key。
     */
    public static final String CURRENT_USER_KEY = "currentUser";

    /**
     * toolContext 中保存会话上下文的 key。
     */
    public static final String SESSION_CONTEXT_KEY = "sessionContext";

    /**
     * toolContext 中保存用户原始消息的 key。
     */
    public static final String MESSAGE_KEY = "message";

    /**
     * toolContext 中保存本轮路由结果的 key。
     */
    public static final String ROUTE_KEY = "route";

    /**
     * toolContext 中保存本轮 Tool 执行状态的 key。
     */
    public static final String STATE_KEY = "toolCallState";

    /**
     * Agent 执行边界守卫。
     */
    private final AgentExecutionGuard executionGuard;

    /**
     * Agent 会话服务，用于执行前的最小指代消解。
     */
    private final AgentSessionService sessionService;

    public SpringAiToolSupport(AgentExecutionGuard executionGuard, AgentSessionService sessionService) {
        this.executionGuard = executionGuard;
        this.sessionService = sessionService;
    }

    /**
     * 通过 Guard 执行 Spring AI 触发的 Tool。
     *
     * @param tool 当前 Tool Bean
     * @param toolContext Spring AI Tool 上下文
     * @param intent 当前意图
     * @param parameters 模型抽取出的结构化参数
     * @return Tool 执行结果
     */
    public AgentToolResult execute(
            AgentTool tool,
            ToolContext toolContext,
            AgentIntent intent,
            AgentToolParameters parameters
    ) {
        CurrentUser currentUser = require(toolContext, CURRENT_USER_KEY, CurrentUser.class);
        AgentSessionContext sessionContext = get(toolContext, SESSION_CONTEXT_KEY, AgentSessionContext.class);
        String message = get(toolContext, MESSAGE_KEY, String.class);
        IntentRoute route = routeOrDefault(toolContext, intent);
        ToolCallPlan plan = ToolCallPlan.builder()
                .intent(intent)
                .toolName(tool.name())
                .parameters(parameters == null ? AgentToolParameters.builder().build() : parameters)
                .llmGenerated(true)
                .reason("Spring AI Tool Calling")
                .build();
        sessionService.resolveReferences(message, sessionContext, plan.getParameters());

        AgentExecutionDecision decision = executionGuard.check(currentUser, message, sessionContext, route, plan);
        AgentToolResult result;
        if (decision.isAllowed()) {
            result = tool.execute(AgentToolRequest.builder()
                    .currentUser(currentUser)
                    .message(message)
                    .context(sessionContext)
                    .route(route)
                    .parameters(plan.getParameters())
                    .build());
        } else {
            result = decision.toToolResult(tool.name());
        }
        capture(toolContext, tool.name(), result);
        return result;
    }

    /**
     * 从 ToolContext 中获取必填对象。
     */
    private <T> T require(ToolContext toolContext, String key, Class<T> type) {
        T value = get(toolContext, key, type);
        if (value == null) {
            throw new IllegalStateException("Spring AI ToolContext 缺少必要字段: " + key);
        }
        return value;
    }

    /**
     * 从 ToolContext 中按类型读取对象。
     */
    private <T> T get(ToolContext toolContext, String key, Class<T> type) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    /**
     * 读取路由结果；缺失时使用当前 Tool 意图构造兜底路由。
     */
    private IntentRoute routeOrDefault(ToolContext toolContext, AgentIntent intent) {
        IntentRoute route = get(toolContext, ROUTE_KEY, IntentRoute.class);
        if (route != null) {
            return route;
        }
        return IntentRoute.builder()
                .intent(intent)
                .confidence(0.50)
                .reason("Spring AI Tool Calling fallback route")
                .build();
    }

    /**
     * 捕获 Tool 执行结果，供 AgentFacade 更新上下文和组装响应。
     */
    private void capture(ToolContext toolContext, String toolName, AgentToolResult result) {
        if (toolContext == null) {
            return;
        }
        Map<String, Object> context = toolContext.getContext();
        if (context == null) {
            return;
        }
        Object stateValue = context.get(STATE_KEY);
        if (stateValue instanceof SpringAiToolCallState state) {
            state.setToolName(toolName);
            state.setResult(result);
        }
    }
}
