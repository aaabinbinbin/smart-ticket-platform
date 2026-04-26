package com.smartticket.agent.service;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionDecisionStatus;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.execution.AgentExecutionMode;
import com.smartticket.agent.execution.AgentExecutionPolicy;
import com.smartticket.agent.execution.AgentExecutionPolicyService;
import com.smartticket.agent.execution.DeterministicCommandExecutor;
import com.smartticket.agent.execution.PendingActionCoordinator;
import com.smartticket.agent.execution.PendingActionCoordinator.PendingContinuation;
import com.smartticket.agent.memory.AgentMemoryService;
import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.react.ReadOnlyReactExecutor;
import com.smartticket.agent.react.ReadOnlyReactExecutor.ReadOnlyReactExecution;
import com.smartticket.agent.resilience.AgentBudgetExceededException;
import com.smartticket.agent.resilience.AgentDegradePolicyService;
import com.smartticket.agent.resilience.AgentErrorCode;
import com.smartticket.agent.resilience.AgentRateLimitService;
import com.smartticket.agent.resilience.AgentSessionLockService;
import com.smartticket.agent.resilience.AgentTurnBudget;
import com.smartticket.agent.resilience.AgentTurnBudgetService;
import com.smartticket.agent.reply.AgentReplyRenderer;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.stream.AgentEventSink;
import com.smartticket.agent.stream.NoopAgentEventSink;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.support.SpringAiToolCallState.AgentToolCallRecord;
import com.smartticket.agent.trace.AgentTraceContext;
import com.smartticket.agent.trace.AgentTraceService;
import com.smartticket.biz.model.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 对外门面服务。
 *
 * <p>该类仍然是当前阶段 `/api/agent/chat` 的统一入口，负责串联会话加载、pending 恢复、意图路由、
 * 执行策略选择、结果渲染、状态提交与 trace 收尾。P5 开始，只读 ReAct 执行细节已经下沉到
 * {@link ReadOnlyReactExecutor}，避免 AgentFacade 继续直接拼装工具目录和模型调用链。
 * 该类会修改 session/memory/pendingAction/trace，但写操作仍然必须委托给确定性命令链路。</p>
 */
@Service
public class AgentFacade {
    private static final Logger log = LoggerFactory.getLogger(AgentFacade.class);

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final boolean chatEnabled;
    private final IntentRouter intentRouter;
    private final AgentSessionService sessionService;
    private final AgentExecutionGuard executionGuard;
    private final AgentPlanner agentPlanner;
    private final SkillRegistry skillRegistry;
    private final AgentMemoryService memoryService;
    private final AgentTraceService traceService;
    private final AgentToolParameterExtractor parameterExtractor;
    private final AgentReplyRenderer agentReplyRenderer;
    private final PendingActionCoordinator pendingActionCoordinator;
    private final DeterministicCommandExecutor deterministicCommandExecutor;
    private final AgentExecutionPolicyService executionPolicyService;
    private final ReadOnlyReactExecutor readOnlyReactExecutor;
    private final AgentRateLimitService rateLimitService;
    private final AgentSessionLockService sessionLockService;
    private final AgentTurnBudgetService budgetService;
    private final AgentDegradePolicyService degradePolicyService;

    public AgentFacade(
            ObjectProvider<ChatClient> chatClientProvider,
            @Value("${smart-ticket.ai.chat.enabled:false}") boolean chatEnabled,
            IntentRouter intentRouter,
            AgentSessionService sessionService,
            AgentExecutionGuard executionGuard,
            AgentPlanner agentPlanner,
            SkillRegistry skillRegistry,
            AgentMemoryService memoryService,
            AgentTraceService traceService,
            AgentToolParameterExtractor parameterExtractor,
            AgentReplyRenderer agentReplyRenderer,
            PendingActionCoordinator pendingActionCoordinator,
            DeterministicCommandExecutor deterministicCommandExecutor,
            AgentExecutionPolicyService executionPolicyService,
            ReadOnlyReactExecutor readOnlyReactExecutor,
            AgentRateLimitService rateLimitService,
            AgentSessionLockService sessionLockService,
            AgentTurnBudgetService budgetService,
            AgentDegradePolicyService degradePolicyService
    ) {
        this.chatClientProvider = chatClientProvider;
        this.chatEnabled = chatEnabled;
        this.intentRouter = intentRouter;
        this.sessionService = sessionService;
        this.executionGuard = executionGuard;
        this.agentPlanner = agentPlanner;
        this.skillRegistry = skillRegistry;
        this.memoryService = memoryService;
        this.traceService = traceService;
        this.parameterExtractor = parameterExtractor;
        this.agentReplyRenderer = agentReplyRenderer;
        this.pendingActionCoordinator = pendingActionCoordinator;
        this.deterministicCommandExecutor = deterministicCommandExecutor;
        this.executionPolicyService = executionPolicyService;
        this.readOnlyReactExecutor = readOnlyReactExecutor;
        this.rateLimitService = rateLimitService;
        this.sessionLockService = sessionLockService;
        this.budgetService = budgetService;
        this.degradePolicyService = degradePolicyService;
    }

    /* ==========================================================
     * 入口
     * ========================================================== */

    /**
     * 处理用户单轮对话消息。
     *
     * @param currentUser 当前登录用户
     * @param sessionId 会话 ID
     * @param message 用户消息
     * @return 对外返回的 Agent 对话结果
     */
    public AgentChatResult chat(CurrentUser currentUser, String sessionId, String message) {
        return chatWithSink(currentUser, sessionId, message, NoopAgentEventSink.INSTANCE);
    }

    /**
     * 处理用户单轮流式对话消息。
     *
     * <p>P7 新增的 SSE 入口复用同步主链，只额外通过 sink 输出 accepted、route、status、delta、
     * final 和 error 事件。该方法不会改变 `/api/agent/chat` 的响应协议；写操作仍然走确定性命令链路，
     * ReAct 仍然只允许只读场景。方法会修改 session/memory/pendingAction/trace，修改点与同步入口相同。</p>
     *
     * @param currentUser 当前登录用户
     * @param sessionId 会话 ID
     * @param message 用户消息
     * @param sink 事件输出器
     * @return 完整 Agent 对话结果，SSE final 事件也会发送该对象
     */
    public AgentChatResult chatStream(CurrentUser currentUser, String sessionId, String message, AgentEventSink sink) {
        return chatWithSink(currentUser, sessionId, message, sink == null ? NoopAgentEventSink.INSTANCE : sink);
    }

    private AgentChatResult chatWithSink(CurrentUser currentUser, String sessionId, String message, AgentEventSink sink) {
        sink.accepted(sessionId);
        AgentTraceContext trace = traceService.start(currentUser, sessionId, message);
        if (!rateLimitService.tryAcquire(currentUser, sessionId)) {
            // 限流发生在主链加载 session 之前，避免高压请求继续消耗模型、RAG 或数据库资源。
            return rejectBeforeMainFlow(sessionId, trace, sink, AgentErrorCode.AGENT_RATE_LIMITED, "Agent 请求触发限流");
        }
        if (!sessionLockService.tryLock(sessionId)) {
            // 同一 session 并发请求直接快速失败，保护 pendingAction 和 recentMessages 不被串写。
            return rejectBeforeMainFlow(sessionId, trace, sink, AgentErrorCode.AGENT_SESSION_BUSY, "同一 session 已有请求处理中");
        }
        try {
            AgentChatResult result = handleChat(currentUser, sessionId, message, trace, sink);
            sink.finalResult(result);
            return result;
        } catch (AgentBudgetExceededException ex) {
            traceService.step(trace, "resilience", "budget", null, ex.getErrorCode().name(), ex.getMessage());
            return rejectBeforeMainFlow(sessionId, trace, sink, ex.getErrorCode(), ex.getMessage());
        } finally {
            sessionLockService.unlock(sessionId);
            sink.closeQuietly();
        }
    }

    private AgentChatResult handleChat(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentTraceContext trace,
            AgentEventSink sink
    ) {
        boolean springAiReady = isSpringAiChatReady();
        AgentSessionContext context = sessionService.load(sessionId);
        memoryService.hydrate(currentUser, context);

        if (pendingActionCoordinator.hasPendingAction(context)) {
            sink.status("正在继续上一轮未完成操作");
            return continuePendingTurn(currentUser, sessionId, message, context, springAiReady, trace);
        }

        sink.status("正在识别意图");
        traceService.step(trace, "route", "before", null, "START", message);
        IntentRoute route = intentRouter.route(message, context);
        traceService.step(trace, "route", "after", null, route.getIntent().name(), String.valueOf(route.getConfidence()));
        sink.route(route);
        sink.status("正在生成执行计划");
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);
        traceService.step(trace, "planner", "decision",
                plan.getNextSkillCode(), plan.getNextAction().name(), plan.getCurrentStage().name());

        sink.status("正在解析执行策略");
        AgentExecutionPolicy policy = executionPolicyService.resolve(currentUser, route);
        traceService.step(trace, "policy", "resolve", null,
                policy.getMode().name(), String.valueOf(policy.getAllowedToolNames()));
        AgentTurnBudget budget = budgetService.create(policy);
        traceService.step(trace, "resilience", "budget", null, "READY",
                "llm=" + policy.getMaxLlmCalls()
                        + ",tool=" + policy.getMaxToolCalls()
                        + ",rag=" + policy.getMaxRagCalls()
                        + ",timeout=" + policy.getTimeout());
        log.info("智能体对话开始：sessionId={}, userId={}, intent={}, springAiChatReady={}",
                sessionId, currentUser.getUserId(), route.getIntent(), springAiReady);

        if (policy.getMode() == AgentExecutionMode.CLARIFICATION) {
            sink.status("需要补充意图信息");
            return clarifyLowConfidenceIntent(currentUser, sessionId, message, context, route, plan, springAiReady, trace);
        }

        if (policy.getMode() == AgentExecutionMode.SAFE_FAILURE) {
            sink.status("当前没有可安全执行的技能");
            return failNoAvailableSkill(currentUser, sessionId, message, context, route, plan, springAiReady, trace);
        }

        if (isWritePolicy(policy)) {
            sink.status("正在执行确定性写命令");
            return executeWriteCommand(currentUser, sessionId, message, context, route, plan, springAiReady, trace, budget);
        }

        if (springAiReady && policy.isAllowReact()) {
            sink.status("正在执行只读 ReAct 查询");
            Optional<ReadOnlyReactExecution> reactExecution = readOnlyReactExecutor.execute(
                    currentUser, message, context, route, plan, policy, trace, budget);
            if (reactExecution.isPresent()) {
                return completeReadOnlyReactTurn(
                        currentUser,
                        sessionId,
                        message,
                        context,
                        route,
                        plan,
                        springAiReady,
                        trace,
                        reactExecution.get(),
                        sink
                );
            }
            if (degradePolicyService.canDegradeToDeterministic(route)) {
                traceService.step(trace, "resilience", "degrade", null,
                        AgentErrorCode.AGENT_DEGRADED.name(), "只读 ReAct 不可用，转入确定性链路");
                sink.status("只读增强能力不可用，正在降级为确定性查询");
            }
        }

        // 只读场景在 ReAct 不可用、无白名单工具或模型执行失败时，仍然回退到确定性查询链。
        sink.status("正在执行确定性查询");
        return executeDeterministicReadOnly(currentUser, sessionId, message, context, route, plan, springAiReady, trace, budget);
    }

    /* ==========================================================
     * 低置信度澄清
     * ========================================================== */

    /**
     * 处理策略层安全失败。
     *
     * <p>当当前用户没有任何通过权限和风险过滤的 Skill 时，主链必须在这里停止，
     * 不能继续走只读 fallback 或写命令执行器，否则会重新暴露未授权 tool。该方法只提交失败态
     * session/memory/trace，不执行任何写操作，也不会创建 pendingAction。</p>
     */
    private AgentChatResult failNoAvailableSkill(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        AgentToolResult toolResult = AgentToolResult.builder()
                .invoked(false)
                .status(AgentToolStatus.FAILED)
                .reply("当前用户没有可安全执行该请求的技能，请确认权限或调整请求后重试。")
                .build();
        AgentExecutionSummary summary = summarizeExecution(
                AgentTurnStatus.FAILED,
                AgentExecutionMode.SAFE_FAILURE,
                route.getIntent(),
                null,
                toolResult,
                context == null ? null : context.getPendingAction(),
                null,
                false,
                false
        );
        traceService.step(trace, "policy", "safe-failure", null, "NO_AVAILABLE_SKILL", route.getIntent().name());
        updateSessionAfterTool(currentUser, sessionId, context, route, message, null, toolResult);
        String finalReply = renderReply(route, plan, context, summary);
        traceService.finish(trace, route, plan, summary);
        return toChatResult(sessionId, route, context, summary, springAiReady, plan, trace);
    }

    private AgentChatResult clarifyLowConfidenceIntent(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        agentPlanner.markClarify(plan, route.getReason());
        AgentToolResult toolResult = AgentToolResult.builder()
                .invoked(false)
                .toolName("clarifyIntent")
                .reply("我暂时无法判断你的目标。请明确说明你是想查询工单、创建工单、转派工单，还是检索历史案例。")
                .build();
        traceService.step(trace, "clarify", "reply", null, "NEED_USER", route.getReason());
        updateSessionAfterTool(currentUser, sessionId, context, route, message, null, toolResult);
        AgentExecutionSummary summary = summarizeExecution(
                AgentTurnStatus.NEED_MORE_INFO,
                AgentExecutionMode.CLARIFICATION,
                route.getIntent(),
                null,
                toolResult,
                context == null ? null : context.getPendingAction(),
                null,
                false,
                false
        );
        String finalReply = renderReply(route, plan, context, summary);
        traceService.finish(trace, route, plan, summary);
        return toChatResult(sessionId, route, context, summary, springAiReady, plan, trace);
    }

    /* ==========================================================
     * 确定性写命令 / 只读兜底
     * ========================================================== */

    /**
     * 执行确定性写命令主链。
     *
     * <p>写意图始终由确定性命令执行器负责，避免 Spring AI 在写场景越过后端 Guard 直接触发数据库变更。</p>
     */
    private AgentChatResult executeWriteCommand(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            boolean springAiReady,
            AgentTraceContext trace,
            AgentTurnBudget budget
    ) {
        budgetService.consumeToolCall(budget);
        traceService.step(trace, "command", "draft", plan.getNextSkillCode(), "START", route.getIntent().name());
        AgentExecutionSummary summary = deterministicCommandExecutor.execute(currentUser, message, context, route, plan);
        updateSessionAfterTool(currentUser, sessionId, context, route, message, summary.getParameters(), summary.getPrimaryResult());
        String finalReply = renderReply(route, plan, context, summary);
        traceService.step(trace, "command", "execute",
                summary.getPrimaryResult() == null ? null : summary.getPrimaryResult().getToolName(),
                summary.getStatus().name(),
                summary.getCommandDraft() == null ? null : summary.getCommandDraft().getPreviewText());
        traceService.finish(trace, route, plan, summary);
        return toChatResult(sessionId, route, context, summary, springAiReady, plan, trace);
    }

    /**
     * 只读意图在未使用 ReAct 时的确定性执行路径。
     */
    private AgentChatResult executeDeterministicReadOnly(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            boolean springAiReady,
            AgentTraceContext trace,
            AgentTurnBudget budget
    ) {
        budgetService.consumeToolCall(budget);
        if (route != null && route.getIntent() == AgentIntent.SEARCH_HISTORY) {
            // 历史案例检索属于 RAG 慢资源，和普通工单查询分开计数，方便高压下局部降级。
            budgetService.consumeRagCall(budget);
        }
        AgentSkill skill = skillRegistry.requireByIntent(route.getIntent());
        AgentTool tool = skill.tool();
        AgentToolParameters parameters = parameterExtractor.extract(message, context);
        sessionService.resolveReferences(message, context, parameters);
        ToolCallPlan toolCallPlan = ToolCallPlan.builder()
                .intent(route.getIntent())
                .toolName(tool.name())
                .parameters(parameters)
                .llmGenerated(false)
                .reason("Spring AI ChatClient 不可用或未触发工具调用")
                .build();

        agentPlanner.beforeExecute(plan);
        traceService.step(trace, "fallback", "skill-call", skill.skillCode(), "START", parameters.toString());
        AgentExecutionDecision decision = executionGuard.check(currentUser, message, context, route, toolCallPlan);

        AgentToolResult toolResult;
        if (decision.isAllowed()) {
            toolResult = decision.getTool().execute(AgentToolRequest.builder()
                    .currentUser(currentUser)
                    .message(message)
                    .context(context)
                    .route(route)
                    .parameters(parameters)
                    .build());
        } else if (decision.getStatus() == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            // 只读兜底也必须走统一 pending 入口，不能在 Facade 内部散落修改 pendingAction。
            toolResult = pendingActionCoordinator.prepareConfirmation(context, route, parameters, tool.name(), decision.getReason());
            agentPlanner.markNeedConfirmation(plan, decision.getReason());
        } else {
            toolResult = decision.toToolResult(tool.name());
        }

        pendingActionCoordinator.syncPendingAction(context, route, parameters, toolResult, message);
        if (decision.getStatus() != AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            agentPlanner.afterTool(plan, toolResult);
        }

        updateSessionAfterTool(currentUser, sessionId, context, route, message, parameters, toolResult);
        AgentExecutionSummary summary = summarizeExecution(
                summarizeFallbackStatus(decision, toolResult, context),
                summarizeFallbackMode(decision),
                route.getIntent(),
                parameters,
                toolResult,
                context == null ? null : context.getPendingAction(),
                null,
                false,
                true
        );
        if (springAiReady && degradePolicyService.canDegradeToDeterministic(route)) {
            degradePolicyService.markDegraded(summary, AgentErrorCode.AGENT_DEGRADED, "只读增强能力不可用，已走确定性查询链路");
        }
        String finalReply = renderReply(route, plan, context, summary);
        traceService.step(trace, "fallback", "skill-call", toolResult.getToolName(), toolResult.getStatus().name(), "finished");
        traceService.finish(trace, route, plan, summary);
        return toChatResult(sessionId, route, context, summary, springAiReady, plan, trace);
    }

    /**
     * 统一收尾只读 ReAct 路径。
     *
     * <p>ReadOnlyReactExecutor 只负责执行与收集结果；真正的 session/memory 提交、reply 渲染和 trace finish
     * 仍然在 Facade 统一完成，这样可以继续满足“单轮只在一个收口点提交状态”的约束。</p>
     */
    private AgentChatResult completeReadOnlyReactTurn(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            boolean springAiReady,
            AgentTraceContext trace,
            ReadOnlyReactExecution reactExecution,
            AgentEventSink sink
    ) {
        updateSessionAfterToolCalls(currentUser, sessionId, context, route, message, reactExecution.toolCalls());
        AgentExecutionSummary summary = reactExecution.summary();
        // 只读总结文本可以作为 delta 事件输出；最终事实仍以 final 中的完整 AgentChatResult 为准。
        if (summary != null && summary.getModelReply() != null && !summary.getModelReply().isBlank()) {
            sink.delta(summary.getModelReply());
        }
        String finalReply = renderReply(route, plan, context, summary);
        traceService.finish(trace, route, plan, summary);
        return toChatResult(sessionId, route, context, summary, springAiReady, plan, trace);
    }

    /* ==========================================================
     * PendingAction 续办
     * ========================================================== */

    /**
     * 继续上一轮未完成的 pendingAction。
     */
    private AgentChatResult continuePendingTurn(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        traceService.step(trace, "pending", "restore", context.getPendingAction().getPendingToolName(),
                "START", context.getPendingAction().getPendingIntent().name());
        PendingContinuation continuation = pendingActionCoordinator.continuePendingAction(currentUser, message, context);
        IntentRoute route = continuation.getRoute();
        AgentPlan plan = continuation.getPlan();
        AgentExecutionSummary summary = continuation.getSummary();
        updateSessionAfterTool(currentUser, sessionId, context, route, message, summary.getParameters(), summary.getPrimaryResult());
        String finalReply = renderReply(route, plan, context, summary);
        traceService.finish(trace, route, plan, summary);
        return toChatResult(sessionId, route, context, summary, springAiReady, plan, trace);
    }

    /* ==========================================================
     * 通用工具方法
     * ========================================================== */

    private void updateSessionAfterTool(
            CurrentUser currentUser,
            String sessionId,
            AgentSessionContext context,
            IntentRoute route,
            String message,
            AgentToolParameters parameters,
            AgentToolResult toolResult
    ) {
        // 单工具路径统一在这里提交一次 session/memory，避免多个分支各自保存造成重复提交。
        sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
        memoryService.remember(currentUser, context, route, parameters, toolResult);
        sessionService.save(sessionId, context);
        sessionService.touch(sessionId);  // 每次交互续期 TTL
    }

    /**
     * 只读 ReAct 多工具调用结束后统一提交上下文。
     *
     * <p>这里允许循环内多次更新内存中的 context，但只在所有工具调用结束后保存一次 session，
     * 继续保护 P2 引入的“单轮单次提交”约束。</p>
     */
    private void updateSessionAfterToolCalls(
            CurrentUser currentUser,
            String sessionId,
            AgentSessionContext context,
            IntentRoute route,
            String message,
            List<AgentToolCallRecord> calls
    ) {
        if (calls == null || calls.isEmpty()) {
            return;
        }
        for (AgentToolCallRecord call : calls) {
            if (call == null || call.getResult() == null) {
                continue;
            }
            sessionService.updateAfterTool(sessionId, context, route, message, call.getResult());
            memoryService.remember(currentUser, context, route, null, call.getResult());
        }
        sessionService.save(sessionId, context);
        sessionService.touch(sessionId);  // 每次交互续期 TTL
    }

    /**
     * 把当前分支的原始执行结果收敛为统一摘要。
     *
     * <p>该方法只构造结构化结果，不修改 session/memory/pendingAction/trace。</p>
     */
    private AgentExecutionSummary summarizeExecution(
            AgentTurnStatus status,
            AgentExecutionMode mode,
            AgentIntent intent,
            AgentToolParameters parameters,
            AgentToolResult primaryResult,
            AgentPendingAction pendingAction,
            String modelReply,
            boolean springAiUsed,
            boolean fallbackUsed
    ) {
        return AgentExecutionSummary.builder()
                .status(status)
                .mode(mode)
                .intent(intent)
                .parameters(parameters)
                .primaryResult(primaryResult)
                .pendingAction(pendingAction)
                .modelReply(modelReply)
                .springAiUsed(springAiUsed)
                .fallbackUsed(fallbackUsed)
                .toolInvoked(primaryResult != null && primaryResult.isInvoked())
                .build();
    }

    private String renderReply(
            IntentRoute route,
            AgentPlan plan,
            AgentSessionContext context,
            AgentExecutionSummary summary
    ) {
        String finalReply = agentReplyRenderer.render(route, plan, context, summary);
        summary.setRenderedReply(finalReply);
        return finalReply;
    }

    private AgentTurnStatus summarizeFallbackStatus(
            AgentExecutionDecision decision,
            AgentToolResult toolResult,
            AgentSessionContext context
    ) {
        if (decision != null && decision.getStatus() == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            return AgentTurnStatus.NEED_CONFIRMATION;
        }
        if (toolResult == null) {
            return AgentTurnStatus.FAILED;
        }
        if (toolResult.getStatus() == AgentToolStatus.SUCCESS) {
            return AgentTurnStatus.COMPLETED;
        }
        if (toolResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            return context != null
                    && context.getPendingAction() != null
                    && context.getPendingAction().isAwaitingConfirmation()
                    ? AgentTurnStatus.NEED_CONFIRMATION
                    : AgentTurnStatus.NEED_MORE_INFO;
        }
        return AgentTurnStatus.FAILED;
    }

    private AgentExecutionMode summarizeFallbackMode(AgentExecutionDecision decision) {
        if (decision != null && decision.getStatus() == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            return AgentExecutionMode.HIGH_RISK_CONFIRMATION;
        }
        return AgentExecutionMode.READ_ONLY_DETERMINISTIC;
    }

    private AgentChatResult toChatResult(
            String sessionId,
            IntentRoute route,
            AgentSessionContext context,
            AgentExecutionSummary summary,
            boolean springAiReady,
            AgentPlan plan,
            AgentTraceContext trace
    ) {
        return AgentChatResult.builder()
                .sessionId(sessionId)
                .intent(route.getIntent().name())
                .reply(summary == null ? "" : summary.getRenderedReply())
                .route(route)
                .context(context)
                .result(summary != null && summary.getPrimaryResult() != null ? summary.getPrimaryResult().getData() : null)
                .springAiChatReady(springAiReady)
                .plan(plan)
                .traceId(trace == null ? null : trace.getTraceId())
                .build();
    }

    /**
     * 在请求未进入主业务链时构造兼容响应。
     *
     * <p>限流、session busy、预算超限这类工程保护不能继续加载或修改 session，
     * 因此这里只生成失败 summary、渲染回复并收尾 trace，不触碰 memory 或 pendingAction。</p>
     */
    private AgentChatResult rejectBeforeMainFlow(
            String sessionId,
            AgentTraceContext trace,
            AgentEventSink sink,
            AgentErrorCode errorCode,
            String reason
    ) {
        traceService.step(trace, "resilience", "reject", null, errorCode.name(), reason);
        AgentExecutionSummary summary = degradePolicyService.failure(errorCode, reason);
        String reply = renderReply(null, null, null, summary);
        traceService.finish(trace, null, null, summary);
        AgentChatResult result = AgentChatResult.builder()
                .sessionId(sessionId)
                .reply(reply)
                .springAiChatReady(isSpringAiChatReady())
                .traceId(trace == null ? null : trace.getTraceId())
                .build();
        // 工程保护拒绝时同时输出 error 和 final，前端既能展示错误码，也能复用完整最终结果。
        sink.error(errorCode.name(), reason, result.getTraceId());
        sink.finalResult(result);
        return result;
    }

    private boolean isSpringAiChatReady() {
        return chatEnabled && chatClientProvider.getIfAvailable() != null;
    }

    private boolean isWritePolicy(AgentExecutionPolicy policy) {
        if (policy == null || policy.getMode() == null) {
            return false;
        }
        return policy.getMode() == AgentExecutionMode.WRITE_COMMAND_DRAFT
                || policy.getMode() == AgentExecutionMode.WRITE_COMMAND_EXECUTE
                || policy.getMode() == AgentExecutionMode.HIGH_RISK_CONFIRMATION;
    }
}
