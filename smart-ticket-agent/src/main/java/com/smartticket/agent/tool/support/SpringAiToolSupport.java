package com.smartticket.agent.tool.support;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.resilience.AgentTurnBudget;
import com.smartticket.agent.resilience.AgentTurnBudgetService;
import com.smartticket.agent.service.AgentSessionService;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.support.AgentToolResults;
import com.smartticket.biz.model.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.annotation.Lazy;
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
     * toolContext 中保存当前轮允许暴露工具名白名单的 key。
     */
    public static final String ALLOWED_TOOL_NAMES_KEY = "allowedToolNames";

    /**
     * toolContext 中保存本轮预算对象的 key。
     */
    public static final String TURN_BUDGET_KEY = "turnBudget";

    /**
     * 智能体执行边界守卫。
     */
    private final AgentExecutionGuard executionGuard;

    /**
     * 智能体会话服务，用于执行前的最小指代消解。
     */
    private final AgentSessionService sessionService;

    /**
     * 单轮预算服务，用于 ReAct 工具调用前扣减 Tool/RAG 次数。
     */
    private final AgentTurnBudgetService budgetService;

    /**
     * 构造 Spring AI 工具支撑组件。
     */
    public SpringAiToolSupport(
            @Lazy AgentExecutionGuard executionGuard,
            AgentSessionService sessionService,
            AgentTurnBudgetService budgetService
    ) {
        this.executionGuard = executionGuard;
        this.sessionService = sessionService;
        this.budgetService = budgetService;
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
        // 检查是否已达最大工具调用次数限制
        SpringAiToolCallState state = get(toolContext, STATE_KEY, SpringAiToolCallState.class);
        if (state != null && !state.canContinue()) {
            return AgentToolResults.failed(tool.name(),
                    "已达单次对话最大工具调用次数限制，请总结已有结果回复用户。", null);
        }

        CurrentUser currentUser = require(toolContext, CURRENT_USER_KEY, CurrentUser.class);
        AgentSessionContext sessionContext = get(toolContext, SESSION_CONTEXT_KEY, AgentSessionContext.class);
        String message = get(toolContext, MESSAGE_KEY, String.class);
        IntentRoute route = routeOrDefault(toolContext, intent);
        if (!isAllowedTool(toolContext, tool)) {
            AgentToolResult result = AgentToolResults.failed(
                    tool.name(),
                    "当前执行策略未授权该工具，请改用本轮允许的工具。",
                    null
            );
            capture(toolContext, tool.name(), result);
            return result;
        }
        consumeToolBudget(toolContext, tool);
        // 查询类 ReAct 只能使用只读工具，避免模型在只读推理阶段越权触发写操作。
        if (isReadOnlyReasoningRoute(route) && !isReadOnlyTool(tool)) {
            AgentToolResult result = AgentToolResults.failed(
                    tool.name(),
                    "当前只读推理阶段不允许调用写工具，请改用查询或检索类工具。",
                    null
            );
            capture(toolContext, tool.name(), result);
            return result;
        }
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
     * 在真正执行 Tool 前扣减预算。
     *
     * <p>预算检查放在白名单之后、Guard 之前，是为了让越权工具不占用本轮预算，
     * 同时避免已超预算的 ReAct 继续触发数据库或 RAG 查询。</p>
     */
    private void consumeToolBudget(ToolContext toolContext, AgentTool tool) {
        AgentTurnBudget budget = get(toolContext, TURN_BUDGET_KEY, AgentTurnBudget.class);
        budgetService.consumeToolCall(budget);
        if (tool != null && "searchHistory".equals(tool.name())) {
            budgetService.consumeRagCall(budget);
        }
    }

    /**
     * 判断当前工具是否在执行策略允许的白名单中。
     *
     * <p>P4 开始模型侧即使拿到了函数定义，也必须再次通过后端白名单校验，
     * 避免 toolContext 原始路由与实际执行策略不一致时出现越权工具调用。</p>
     */
    private boolean isAllowedTool(ToolContext toolContext, AgentTool tool) {
        if (tool == null) {
            return false;
        }
        List<String> allowedToolNames = allowedToolNames(toolContext);
        if (allowedToolNames.isEmpty()) {
            return true;
        }
        return allowedToolNames.contains(tool.name());
    }

    /**
     * 判断当前原始路由是否属于只读 ReAct 场景。
     *
     * @param route 本轮原始意图路由
     * @return true 表示本轮只允许查询和检索类工具，不允许写操作
     */
    private boolean isReadOnlyReasoningRoute(IntentRoute route) {
        if (route == null || route.getIntent() == null) {
            return false;
        }
        return route.getIntent() == AgentIntent.QUERY_TICKET
                || route.getIntent() == AgentIntent.SEARCH_HISTORY;
    }

    /**
     * 判断工具是否为只读工具。
     *
     * @param tool 当前准备执行的 Tool
     * @return true 表示该工具不会修改数据库状态，可安全暴露给只读 ReAct
     */
    private boolean isReadOnlyTool(AgentTool tool) {
        return tool != null
                && tool.metadata() != null
                && tool.metadata().isReadOnly();
    }

    /**
     * 从 ToolContext 中读取当前轮允许暴露的工具白名单。
     */
    @SuppressWarnings("unchecked")
    private List<String> allowedToolNames(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return List.of();
        }
        Object value = toolContext.getContext().get(ALLOWED_TOOL_NAMES_KEY);
        if (value instanceof List<?> rawList) {
            return rawList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
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
     * 捕获 Tool 执行结果，追加到调用历史记录中。
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
            state.record(toolName, result);
        }
    }
}
