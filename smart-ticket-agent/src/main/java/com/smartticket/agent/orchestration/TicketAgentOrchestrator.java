package com.smartticket.agent.orchestration;

import com.smartticket.agent.dto.AgentChatRequestDTO;
import com.smartticket.agent.dto.AgentChatResponseDTO;
import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.llm.model.LlmFallbackToolCallPlan;
import com.smartticket.agent.llm.model.LlmToolCallPlan;
import com.smartticket.agent.llm.service.LlmAgentService;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.service.AgentSessionCacheService;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolRegistry;
import com.smartticket.biz.model.CurrentUser;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 单 Agent 编排服务。
 *
 * <p>该类只负责串联阶段九的主流程：读取上下文、构造 fallback、生成计划、校验计划、
 * 执行 Tool、观察结果、生成回复、更新上下文。具体细节拆分到各个协作组件中。</p>
 *
 * <p>LLM 只能提出工具调用计划，不能直接执行工具，更不能绕过 biz 层改变业务数据。</p>
 */
@Service
public class TicketAgentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(TicketAgentOrchestrator.class);

    /**
     * Agent 会话上下文缓存服务。
     */
    private final AgentSessionCacheService sessionCacheService;

    /**
     * 阶段八工作流 fallback 构造服务。
     */
    private final AgentWorkflowFallbackService fallbackService;

    /**
     * Tool 注册表，用于把候选 Tool 元数据传给 LLM。
     */
    private final AgentToolRegistry toolRegistry;

    /** Agent 执行边界守卫，统一处理计划合法性、风险确认和必填参数校验。 */
    private final AgentExecutionGuard executionGuard;

    /**
     * Tool 执行器。
     */
    private final AgentToolExecutor toolExecutor;

    /**
     * Agent 回复生成器。
     */
    private final AgentResponseComposer responseComposer;

    /**
     * Agent 会话上下文更新器。
     */
    private final AgentContextUpdater contextUpdater;

    /** pending action 处理服务，用于优先恢复上一轮未完成动作。 */
    private final AgentPendingActionService pendingActionService;

    /** 简单指代消解器，用于把“它”“刚才那个工单”等映射到会话上下文实体。 */
    private final AgentContextReferenceResolver referenceResolver;

    /**
     * LLM 能力服务，负责生成工具调用计划和参数归一化。
     */
    private final LlmAgentService llmAgentService;

    public TicketAgentOrchestrator(
            AgentSessionCacheService sessionCacheService,
            AgentWorkflowFallbackService fallbackService,
            AgentToolRegistry toolRegistry,
            AgentExecutionGuard executionGuard,
            AgentToolExecutor toolExecutor,
            AgentResponseComposer responseComposer,
            AgentContextUpdater contextUpdater,
            AgentPendingActionService pendingActionService,
            AgentContextReferenceResolver referenceResolver,
            LlmAgentService llmAgentService
    ) {
        this.sessionCacheService = sessionCacheService;
        this.fallbackService = fallbackService;
        this.toolRegistry = toolRegistry;
        this.executionGuard = executionGuard;
        this.toolExecutor = toolExecutor;
        this.responseComposer = responseComposer;
        this.contextUpdater = contextUpdater;
        this.pendingActionService = pendingActionService;
        this.referenceResolver = referenceResolver;
        this.llmAgentService = llmAgentService;
    }

    /**
     * 编排一次 Agent 对话请求。
     */
    public AgentChatResponseDTO chat(CurrentUser currentUser, AgentChatRequestDTO request) {
        // 读取当前会话上下文；如果缓存不存在，SessionCacheService 会返回一个空上下文。
        AgentSessionContext context = sessionCacheService.get(request.getSessionId());
        log.info("agent orchestration context before call: sessionId={}, context={}", request.getSessionId(), context);

        if (pendingActionService.hasPendingAction(context)) {
            return continuePendingAction(currentUser, request, context);
        }

        // 先构造 fallback，用于 LLM 计划失败或校验失败时兜底。
        AgentWorkflowFallback fallback = fallbackService.build(request.getMessage(), context);
        // 尝试让 LLM 生成工具调用计划；失败时直接使用 fallback 计划。
        ToolCallPlan plan = buildPlanOrFallback(request.getMessage(), context, fallback);
        referenceResolver.applyReferences(request.getMessage(), context, plan.getParameters());
        // 根据当前计划生成本轮路由信息，保持 /api/agent/chat 响应结构兼容。
        IntentRoute route = routeFromPlan(fallback.getRoute(), plan);
        // 统一执行前决策：计划合法性、风险确认和必填参数都在 Guard 中收敛。
        AgentExecutionDecision decision = checkOrFallback(currentUser, request.getMessage(), context, route, fallback, plan);
        // 如果 LLM 计划被拒绝，则切换到 fallback 计划，并重新经过 Guard 决策。
        if (decision.isRejected() && plan.isLlmGenerated()) {
            plan = fallback.getPlan();
            referenceResolver.applyReferences(request.getMessage(), context, plan.getParameters());
            route = routeFromPlan(fallback.getRoute(), plan);
            decision = executionGuard.check(currentUser, request.getMessage(), context, route, plan);
        }
        // 如果 fallback 计划仍然被拒绝，则返回显式失败，避免带着无效 Tool 进入执行器。
        if (decision.isRejected()) {
            AgentToolResult failedResult = decision.toToolResult(plan.getToolName());
            responseComposer.refineReply(request.getMessage(), route, failedResult);
            contextUpdater.apply(context, route, request.getMessage(), failedResult);
            sessionCacheService.save(request.getSessionId(), context);
            return buildResponse(request, route, context, failedResult);
        }

        log.info("agent tool plan selected: sessionId={}, plan={}, decision={}",
                request.getSessionId(), plan, decision);

        // 执行已被 Guard 放行的 Tool；如需确认或补参，执行器会直接返回 Guard 生成的非执行结果。
        AgentToolResult toolResult = toolExecutor.execute(
                currentUser,
                request.getMessage(),
                context,
                route,
                plan,
                decision
        );
        log.info("agent tool observation: sessionId={}, tool={}, status={}, invoked={}, data={}",
                request.getSessionId(), toolResult.getToolName(), toolResult.getStatus(),
                toolResult.isInvoked(), toolResult.getData());

        // 使用 LLM 优化回复文本
        responseComposer.refineReply(request.getMessage(), route, toolResult);
        // 更新会话上下文到context里面
        contextUpdater.apply(context, route, request.getMessage(), toolResult);
        pendingActionService.refreshPendingAction(context, route, plan, toolResult);
        // 将最新的会话上下文存到redis里面
        sessionCacheService.save(request.getSessionId(), context);
        log.info("agent orchestration context after call: sessionId={}, context={}", request.getSessionId(), context);

        // 返回响应信息
        return buildResponse(request, route, context, toolResult);
    }

    /**
     * 优先恢复上一轮未完成的 pending action。
     *
     * <p>该分支不会重新做完整路由，而是把用户本轮消息视为补充参数或确认信息，合并到上一轮计划后继续
     * 通过 AgentToolExecutor 执行。执行完成后再按状态决定是否清理 pending action。</p>
     */
    private AgentChatResponseDTO continuePendingAction(
            CurrentUser currentUser,
            AgentChatRequestDTO request,
            AgentSessionContext context
    ) {
        AgentPendingActionResumeResult resumeResult = pendingActionService.resume(
                currentUser,
                request.getMessage(),
                context
        );
        IntentRoute route = resumeResult.getRoute();
        ToolCallPlan plan = resumeResult.getPlan();
        AgentToolResult toolResult = resumeResult.getToolResult();
        log.info("agent pending action observation: sessionId={}, tool={}, status={}, invoked={}, data={}",
                request.getSessionId(), toolResult.getToolName(), toolResult.getStatus(),
                toolResult.isInvoked(), toolResult.getData());

        responseComposer.refineReply(request.getMessage(), route, toolResult);
        contextUpdater.apply(context, route, request.getMessage(), toolResult);
        pendingActionService.refreshPendingAction(context, route, plan, toolResult);
        sessionCacheService.save(request.getSessionId(), context);
        log.info("agent orchestration context after pending call: sessionId={}, context={}",
                request.getSessionId(), context);
        return buildResponse(request, route, context, toolResult);
    }

    /**
     * 构造 /api/agent/chat 的兼容响应。
     */
    private AgentChatResponseDTO buildResponse(
            AgentChatRequestDTO request,
            IntentRoute route,
            AgentSessionContext context,
            AgentToolResult toolResult
    ) {
        return AgentChatResponseDTO.builder()
                .sessionId(request.getSessionId())
                .intent(route.getIntent().name())
                .reply(toolResult.getReply())
                .route(route)
                .context(context)
                .result(toolResult.getData())
                .build();
    }

    /**
     * 使用 LLM 尝试生成工具调用计划，失败时使用阶段八 fallback 计划。
     */
    private ToolCallPlan buildPlanOrFallback(
            String message,
            AgentSessionContext context,
            AgentWorkflowFallback fallback
    ) {
        Optional<LlmToolCallPlan> llmPlan = llmAgentService.planToolCall(
                message,
                context,
                fallback.getRoute(),
                toLlmFallbackPlan(fallback.getPlan()),
                toolRegistry.allTools()
        );
        return llmPlan.flatMap(plan -> normalizePlan(plan, fallback.getPlan()))
                .orElse(fallback.getPlan());
    }

    /**
     * 将编排层 fallback 计划转换为 LLM 层 DTO，避免 LLM 层反向依赖编排层对象。
     */
    private LlmFallbackToolCallPlan toLlmFallbackPlan(ToolCallPlan fallbackPlan) {
        return LlmFallbackToolCallPlan.builder()
                .intent(fallbackPlan.getIntent())
                .toolName(fallbackPlan.getToolName())
                .parameters(fallbackPlan.getParameters())
                .reason(fallbackPlan.getReason())
                .build();
    }

    /**
     * 将 LLM 原始计划转换为编排层计划。
     */
    private Optional<ToolCallPlan> normalizePlan(LlmToolCallPlan llmPlan, ToolCallPlan fallbackPlan) {
        if (llmPlan.getIntent() == null || llmPlan.getToolName() == null || llmPlan.getToolName().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(ToolCallPlan.builder()
                .intent(llmPlan.getIntent())
                .toolName(llmPlan.getToolName())
                .parameters(llmAgentService.mergeParametersOrFallback(
                        llmPlan.getParameters(), fallbackPlan.getParameters()))
                .llmGenerated(true)
                .reason(hasText(llmPlan.getReason()) ? "LLM plan: " + llmPlan.getReason() : "LLM 工具调用计划")
                .build());
    }

    /**
     * 执行前决策；如果 LLM 计划被拒绝，由调用方回退到阶段八计划。
     */
    private AgentExecutionDecision checkOrFallback(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentWorkflowFallback fallback,
            ToolCallPlan plan
    ) {
        AgentExecutionDecision decision = executionGuard.check(currentUser, message, context, route, plan);
        if (!decision.isRejected() || !plan.isLlmGenerated()) {
            return decision;
        }
        log.info("agent llm plan fallback: reason={}, fallbackPlan={}", decision.getReason(), fallback.getPlan());
        return decision;
    }

    /**
     * 根据计划生成本轮响应中的路由信息。
     */
    private IntentRoute routeFromPlan(IntentRoute fallbackRoute, ToolCallPlan plan) {
        return IntentRoute.builder()
                .intent(plan.getIntent())
                .confidence(fallbackRoute.getConfidence())
                .reason(plan.getReason())
                .build();
    }

    /**
     * 字符串非空判断。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
