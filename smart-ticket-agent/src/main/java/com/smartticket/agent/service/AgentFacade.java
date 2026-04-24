package com.smartticket.agent.service;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionDecisionStatus;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.memory.AgentMemoryService;
import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanAction;
import com.smartticket.agent.planner.AgentPlanStage;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.prompt.PromptTemplateService;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.support.SpringAiToolCallState;
import com.smartticket.agent.tool.support.SpringAiToolCallState.AgentToolCallRecord;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.agent.trace.AgentTraceContext;
import com.smartticket.agent.trace.AgentTraceService;
import com.smartticket.biz.model.CurrentUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 智能体对话门面服务。
 *
 * <p>负责串联意图路由、ReAct 智能体循环、执行守卫、工具调用、记忆更新和轨迹记录。
 * 当 Spring AI 可用时优先走 LLM 驱动的多步推理循环（ReAct），让 LLM 自主决定
 * 调用哪些工具、以什么顺序调用；否则回退到确定性工具执行流程。</p>
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
    private final PromptTemplateService promptTemplateService;
    private final AgentToolParameterExtractor parameterExtractor;

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
            PromptTemplateService promptTemplateService,
            AgentToolParameterExtractor parameterExtractor
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
        this.promptTemplateService = promptTemplateService;
        this.parameterExtractor = parameterExtractor;
    }

    /* ==========================================================
     * 入口
     * ========================================================== */

    /**
     * 处理用户对话消息。
     */
    public AgentChatResult chat(CurrentUser currentUser, String sessionId, String message) {
        boolean springAiReady = isSpringAiChatReady();
        AgentTraceContext trace = traceService.start(currentUser, sessionId, message);
        AgentSessionContext context = sessionService.load(sessionId);
        memoryService.hydrate(currentUser, context);

        if (hasPendingConfirmation(context)) {
            return continuePendingConfirmation(currentUser, sessionId, message, context, springAiReady, trace);
        }
        if (hasPendingCreateDraft(context)) {
            return continuePendingCreate(currentUser, sessionId, message, context, springAiReady, trace);
        }

        traceService.step(trace, "route", "before", null, "START", message);
        IntentRoute route = intentRouter.route(message, context);
        traceService.step(trace, "route", "after", null, route.getIntent().name(), String.valueOf(route.getConfidence()));
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);
        traceService.step(trace, "planner", "decision", plan.getNextSkillCode(), plan.getNextAction().name(), plan.getCurrentStage().name());
        log.info("智能体对话开始：sessionId={}, userId={}, intent={}, springAiChatReady={}",
                sessionId, currentUser.getUserId(), route.getIntent(), springAiReady);

        if (route.getConfidence() < 0.50d) {
            return clarifyLowConfidenceIntent(currentUser, sessionId, message, context, route, plan, springAiReady, trace);
        }

        // ReAct 智能体循环：LLM 自主决定工具调用序列
        if (springAiReady) {
            Optional<AgentChatResult> reactResult = agentReActLoop(
                    currentUser, sessionId, message, context, route, plan, trace);
            if (reactResult.isPresent()) {
                return reactResult.get();
            }
        }

        // 回退：确定性单步执行
        return executeDeterministicFallback(currentUser, sessionId, message, context, route, plan, springAiReady, trace);
    }

    /* ==========================================================
     * ReAct 智能体循环（核心改造）
     * ========================================================== */

    /**
     * ReAct 智能体循环：使用 streaming 捕获 LLM 全部中间推理过程。
     * 注册全部工具让 LLM 自主决策，Spring AI 内部处理多步工具调用，
     * streaming flux 包含每轮工具调用前 LLM 的思考文本。
     */
    private Optional<AgentChatResult> agentReActLoop(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            AgentTraceContext trace
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return Optional.empty();
        }

        SpringAiToolCallState callState = new SpringAiToolCallState();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(SpringAiToolSupport.CURRENT_USER_KEY, currentUser);
        toolContext.put(SpringAiToolSupport.SESSION_CONTEXT_KEY, context);
        toolContext.put(SpringAiToolSupport.MESSAGE_KEY, message);
        toolContext.put(SpringAiToolSupport.ROUTE_KEY, route);
        toolContext.put(SpringAiToolSupport.STATE_KEY, callState);

        try {
            agentPlanner.beforeExecute(plan);
            // 标记进入 LLM 多步推理阶段，与确定性执行路径区分
            plan.setCurrentStage(AgentPlanStage.AGENT_THINKING);
            plan.setNextAction(AgentPlanAction.REACT_REASONING);
            traceService.step(trace, "agent", "react-loop", null, "START", route.getIntent().name());

            String fullContext = buildConversationContext(context, message, route);
            String system = agentSystemPrompt();

            // 第 1 步：使用 streaming 捕获完整 LLM 输出（含中间推理）
            Flux<ChatResponse> responseFlux = chatClient.prompt()
                    .system(system)
                    .user(fullContext)
                    .tools(allTools())
                    .toolContext(toolContext)
                    .stream()
                    .chatResponse();

            List<ChatResponse> allResponses = responseFlux
                    .collectList()
                    .block(Duration.ofSeconds(120));

            // 第 2 步：从所有 streaming chunk 中提取推理链
            StringBuilder reasoningBuffer = new StringBuilder();
            for (ChatResponse response : allResponses) {
                if (response.getResult() != null && response.getResult().getOutput() != null) {
                    String text = response.getResult().getOutput().getText();
                    if (hasText(text)) {
                        reasoningBuffer.append(text);
                    }
                }
            }
            String llmOutput = reasoningBuffer.toString().trim();
            if (hasText(llmOutput)) {
                traceService.recordReasoning(trace, llmOutput);
            }

            // 第 3 步：处理工具调用结果
            List<AgentToolCallRecord> allCalls = callState.getAllCalls();
            for (AgentToolCallRecord call : allCalls) {
                traceService.step(trace, "agent", "tool-call", call.getToolName(),
                        call.getResult() != null ? call.getResult().getStatus().name() : "UNKNOWN", "react-step");
            }
            agentPlanner.recordToolCalls(plan, allCalls);

            // 第 4 步：更新会话和记忆
            AgentToolResult lastResult = callState.getResult();
            for (AgentToolCallRecord call : allCalls) {
                updateSessionAfterTool(currentUser, sessionId, context, route, message, null, call.getResult());
            }

            String finalReply = hasText(llmOutput) ? llmOutput : (lastResult != null ? lastResult.getReply() : "");
            traceService.finish(trace, route, plan, null, lastResult, finalReply, true, false);
            return Optional.of(toChatResult(sessionId, route, context, lastResult, finalReply, true, plan, trace));

        } catch (RuntimeException ex) {
            log.warn("Agent ReAct 循环失败(sessionId={}), 降级到非流式调用: {}", sessionId, ex.getMessage());
            traceService.step(trace, "agent", "react-loop", null, "RETRY_CALL", ex.getMessage());

            // 降级到非流式调用
            return tryNonStreamingAgentCall(currentUser, sessionId, message,
                    context, route, plan, trace, callState);
        }
    }

    /**
     * 非流式降级调用：当 streaming 不可用时使用普通的 call()。
     */
    private Optional<AgentChatResult> tryNonStreamingAgentCall(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            AgentTraceContext trace,
            SpringAiToolCallState callState
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) return Optional.empty();

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(SpringAiToolSupport.CURRENT_USER_KEY, currentUser);
        toolContext.put(SpringAiToolSupport.SESSION_CONTEXT_KEY, context);
        toolContext.put(SpringAiToolSupport.MESSAGE_KEY, message);
        toolContext.put(SpringAiToolSupport.ROUTE_KEY, route);
        toolContext.put(SpringAiToolSupport.STATE_KEY, callState);

        try {
            plan.setCurrentStage(AgentPlanStage.AGENT_THINKING);
            plan.setNextAction(AgentPlanAction.REACT_REASONING);
            String content = chatClient.prompt()
                    .system(agentSystemPrompt())
                    .user(buildConversationContext(context, message, route))
                    .tools(allTools())
                    .toolContext(toolContext)
                    .call()
                    .content();

            // 非流式无法捕获中间推理，记录一个简要说明
            traceService.recordReasoning(trace, "[非流式模式，中间推理过程未记录]");

            List<AgentToolCallRecord> allCalls = callState.getAllCalls();
            for (AgentToolCallRecord call : allCalls) {
                traceService.step(trace, "agent", "tool-call", call.getToolName(),
                        call.getResult() != null ? call.getResult().getStatus().name() : "UNKNOWN", "react-step");
            }
            agentPlanner.recordToolCalls(plan, allCalls);

            AgentToolResult lastResult = callState.getResult();
            for (AgentToolCallRecord call : allCalls) {
                updateSessionAfterTool(currentUser, sessionId, context, route, message, null, call.getResult());
            }

            String finalReply = hasText(content) ? content : (lastResult != null ? lastResult.getReply() : "");
            traceService.finish(trace, route, plan, null, lastResult, finalReply, true, false);
            return Optional.of(toChatResult(sessionId, route, context, lastResult, finalReply, true, plan, trace));

        } catch (RuntimeException ex) {
            log.warn("非流式 Agent 调用也失败，使用确定性回退：sessionId={}, reason={}", sessionId, ex.getMessage());
            traceService.step(trace, "agent", "react-loop", null, "FAILED", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 返回全部 LLM 可调用的工具对象数组。Spring AI 据此向 LLM 展示全部函数定义。
     */
    private Object[] allTools() {
        return skillRegistry.allSkills().stream()
                .map(AgentSkill::tool)
                .toArray(Object[]::new);
    }

    /**
     * 构建系统提示词：基于 v2 智能体 prompt，描述完整能力空间。
     */
    private String agentSystemPrompt() {
        String base = promptTemplateService.content("agent-user-prompt",
                "你是一个企业智能工单平台的 AI 智能体。你可以使用以下工具："
                + "queryTicket（查询工单）、createTicket（创建工单）、"
                + "transferTicket（转派工单，高风险操作）、searchHistory（检索历史案例）。");
        // 附加工具详细说明
        String createRef = promptTemplateService.content("create-ticket-completion", "");
        String historyRef = promptTemplateService.content("history-summary", "");
        String resultGuide = promptTemplateService.content("result-explanation", "");
        StringBuilder sb = new StringBuilder(base);
        if (hasText(createRef)) sb.append("\n\n【createTicket 工具说明】\n").append(createRef);
        if (hasText(historyRef)) sb.append("\n\n【searchHistory 工具说明】\n").append(historyRef);
        if (hasText(resultGuide)) sb.append("\n\n【回复风格】\n").append(resultGuide);
        return sb.toString();
    }

    /**
     * 构建用户上下文：包含对话历史、当前消息、系统路由信息。
     */
    private String buildConversationContext(AgentSessionContext context, String message, IntentRoute route) {
        StringBuilder sb = new StringBuilder();

        // 对话历史
        List<String> recentMessages = context != null ? context.getRecentMessages() : null;
        if (recentMessages != null && !recentMessages.isEmpty()) {
            sb.append("## 对话历史\n");
            for (String msg : recentMessages) {
                sb.append(msg).append("\n");
            }
            sb.append("\n");
        }

        // 当前消息
        sb.append("## 当前用户消息\n").append(message).append("\n\n");

        // 系统已识别的上下文
        sb.append("## 系统上下文\n");
        sb.append("系统识别意图：").append(route.getIntent().name());
        sb.append("（置信度：").append(String.format("%.0f%%", route.getConfidence() * 100)).append("）\n");
        sb.append("识别依据：").append(route.getReason()).append("\n");
        if (context != null && context.getActiveTicketId() != null) {
            sb.append("当前活跃工单 ID：").append(context.getActiveTicketId()).append("\n");
        }

        // 工作记忆摘要
        if (context != null && context.getWorkingMemory() != null && context.getWorkingMemory().getLastToolSummary() != null) {
            sb.append("上次操作摘要：").append(context.getWorkingMemory().getLastToolSummary()).append("\n");
        }
        return sb.toString();
    }

    /* ==========================================================
     * 低置信度澄清
     * ========================================================== */

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
        traceService.finish(trace, route, plan, null, toolResult, toolResult.getReply(), false, false);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
    }

    /* ==========================================================
     * 确定性回退流程（Spring AI 不可用时）
     * ========================================================== */

    private AgentChatResult executeDeterministicFallback(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
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
            toolResult = buildConfirmationResult(tool.name(), route, parameters, decision.getReason());
            context.setPendingAction(AgentPendingAction.builder()
                    .pendingIntent(route.getIntent())
                    .pendingToolName(tool.name())
                    .pendingParameters(copyParameters(parameters))
                    .awaitingConfirmation(true)
                    .confirmationSummary(toolResult.getReply())
                    .lastToolResult(toolResult)
                    .build());
            agentPlanner.markNeedConfirmation(plan, decision.getReason());
        } else {
            toolResult = decision.toToolResult(tool.name());
        }
        syncCreatePendingAction(context, route, parameters, message, toolResult);
        if (decision.getStatus() != AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            agentPlanner.afterTool(plan, toolResult);
        }
        updateSessionAfterTool(currentUser, sessionId, context, route, message, parameters, toolResult);
        traceService.step(trace, "fallback", "skill-call", toolResult.getToolName(), toolResult.getStatus().name(), "finished");
        traceService.finish(trace, route, plan, parameters, toolResult, toolResult.getReply(), false, true);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
    }

    /* ==========================================================
     * 待确认 / 待创建处理（保持不变）
     * ========================================================== */

    private AgentChatResult continuePendingConfirmation(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        AgentPendingAction pendingAction = context.getPendingAction();
        IntentRoute route = IntentRoute.builder()
                .intent(pendingAction.getPendingIntent())
                .confidence(0.99d)
                .reason("继续等待高风险操作确认")
                .build();
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);
        traceService.step(trace, "human-confirmation", "pending", pendingAction.getPendingToolName(), "WAITING", pendingAction.getConfirmationSummary());

        if (isCancelMessage(message)) {
            context.setPendingAction(null);
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(pendingAction.getPendingToolName())
                    .reply("已取消本次高风险操作，未执行任何变更。")
                    .build();
            updateSessionAfterTool(currentUser, sessionId, context, route, message, pendingAction.getPendingParameters(), toolResult);
            agentPlanner.afterTool(plan, toolResult);
            traceService.finish(trace, route, plan, pendingAction.getPendingParameters(), toolResult, toolResult.getReply(), false, false);
            return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
        }

        if (!isConfirmMessage(message)) {
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.NEED_MORE_INFO)
                    .toolName(pendingAction.getPendingToolName())
                    .reply(pendingAction.getConfirmationSummary() + "\n\n请回复「确认执行」继续，或回复「取消」放弃。")
                    .build();
            updateSessionAfterTool(currentUser, sessionId, context, route, message, pendingAction.getPendingParameters(), toolResult);
            traceService.finish(trace, route, plan, pendingAction.getPendingParameters(), toolResult, toolResult.getReply(), false, false);
            return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
        }

        AgentTool tool = toolForName(pendingAction.getPendingToolName());
        AgentToolParameters parameters = copyParameters(pendingAction.getPendingParameters());
        context.setPendingAction(null);
        AgentToolResult toolResult = tool.execute(AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(parameters)
                .build());
        agentPlanner.afterTool(plan, toolResult);
        updateSessionAfterTool(currentUser, sessionId, context, route, message, parameters, toolResult);
        traceService.step(trace, "human-confirmation", "execute", tool.name(), toolResult.getStatus().name(), "confirmed");
        traceService.finish(trace, route, plan, parameters, toolResult, toolResult.getReply(), false, false);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
    }

    private AgentChatResult continuePendingCreate(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        IntentRoute route = IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.99d)
                .reason("继续补全待创建工单草稿")
                .build();
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);
        traceService.step(trace, "planner", "pending-create", plan.getNextSkillCode(), plan.getNextAction().name(), plan.getCurrentStage().name());
        if (isCancelMessage(message)) {
            context.setPendingAction(null);
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(toolForIntent(AgentIntent.CREATE_TICKET).name())
                    .reply("已取消本次工单创建。你可以随时重新发起新的创建请求。")
                    .build();
            updateSessionAfterTool(currentUser, sessionId, context, route, message, null, toolResult);
            agentPlanner.afterTool(plan, toolResult);
            traceService.finish(trace, route, plan, null, toolResult, toolResult.getReply(), false, false);
            return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
        }

        AgentTool createTool = toolForIntent(AgentIntent.CREATE_TICKET);
        AgentPendingAction pendingAction = context.getPendingAction();
        AgentToolParameters mergedParameters = mergeCreateDraftParameters(
                pendingAction.getPendingParameters(),
                parameterExtractor.extract(message, context),
                message,
                pendingAction.getAwaitingFields()
        );
        AgentToolResult toolResult = createTool.execute(AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(mergedParameters)
                .build());
        syncCreatePendingAction(context, route, mergedParameters, message, toolResult);
        agentPlanner.afterTool(plan, toolResult);
        updateSessionAfterTool(currentUser, sessionId, context, route, message, mergedParameters, toolResult);
        traceService.finish(trace, route, plan, mergedParameters, toolResult, toolResult.getReply(), false, false);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
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
        sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
        memoryService.remember(currentUser, context, route, parameters, toolResult);
        sessionService.save(sessionId, context);
    }

    private boolean hasPendingCreateDraft(AgentSessionContext context) {
        return context != null
                && context.getPendingAction() != null
                && context.getPendingAction().getPendingIntent() == AgentIntent.CREATE_TICKET
                && !context.getPendingAction().isAwaitingConfirmation();
    }

    private boolean hasPendingConfirmation(AgentSessionContext context) {
        return context != null
                && context.getPendingAction() != null
                && context.getPendingAction().isAwaitingConfirmation();
    }

    private AgentToolResult buildConfirmationResult(
            String toolName,
            IntentRoute route,
            AgentToolParameters parameters,
            String reason
    ) {
        String summary = switch (route.getIntent()) {
            case TRANSFER_TICKET -> "高风险操作需要确认。\n"
                    + "操作：转派工单\n"
                    + "工单 ID：" + valueOrUnknown(parameters == null ? null : parameters.getTicketId()) + "\n"
                    + "目标处理人 ID：" + valueOrUnknown(parameters == null ? null : parameters.getAssigneeId()) + "\n"
                    + "原因：" + reason + "\n"
                    + "请回复「确认执行」继续，或回复「取消」放弃。";
            default -> "该操作风险较高，需要二次确认后才能执行。请回复「确认执行」继续，或回复「取消」放弃。";
        };
        return AgentToolResult.builder()
                .invoked(false)
                .status(AgentToolStatus.NEED_MORE_INFO)
                .toolName(toolName)
                .reply(summary)
                .data(parameters)
                .activeTicketId(parameters == null ? null : parameters.getTicketId())
                .activeAssigneeId(parameters == null ? null : parameters.getAssigneeId())
                .build();
    }

    private void syncCreatePendingAction(
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters parameters,
            String message,
            AgentToolResult toolResult
    ) {
        if (context == null || route.getIntent() != AgentIntent.CREATE_TICKET) {
            return;
        }
        if (toolResult.getStatus() == AgentToolStatus.SUCCESS) {
            context.setPendingAction(null);
            return;
        }
        if (toolResult.getStatus() != AgentToolStatus.NEED_MORE_INFO) {
            return;
        }
        List<AgentToolParameterField> missingFields = extractMissingFields(toolResult.getData());
        AgentToolParameters draft = parameters == null ? AgentToolParameters.builder().build() : copyParameters(parameters);
        context.setPendingAction(AgentPendingAction.builder()
                .pendingIntent(AgentIntent.CREATE_TICKET)
                .pendingToolName(toolForIntent(AgentIntent.CREATE_TICKET).name())
                .pendingParameters(draft)
                .awaitingFields(missingFields)
                .lastToolResult(toolResult)
                .build());
        toolResult.setReply(buildCreateClarificationReply(missingFields, draft, message));
    }

    private List<AgentToolParameterField> extractMissingFields(Object data) {
        if (!(data instanceof List<?> rawList)) return List.of();
        List<AgentToolParameterField> fields = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof AgentToolParameterField field) fields.add(field);
        }
        return fields;
    }

    private AgentToolParameters mergeCreateDraftParameters(
            AgentToolParameters draft,
            AgentToolParameters extracted,
            String message,
            List<AgentToolParameterField> awaitingFields
    ) {
        AgentToolParameters merged = copyParameters(draft == null ? AgentToolParameters.builder().build() : draft);
        if (extracted.getType() != null) merged.setType(extracted.getType());
        if (extracted.getCategory() != null) merged.setCategory(extracted.getCategory());
        if (extracted.getPriority() != null) merged.setPriority(extracted.getPriority());
        boolean metadataOnly = isLikelyMetadataOnlyMessage(extracted, message);
        if (!metadataOnly && shouldFillTitle(merged, awaitingFields, extracted)) {
            merged.setTitle(extracted.getTitle());
        }
        if (!metadataOnly && shouldFillDescription(merged, awaitingFields, extracted, message)) {
            merged.setDescription(message == null ? null : message.trim());
        }
        if (hasText(extracted.getIdempotencyKey())) {
            merged.setIdempotencyKey(extracted.getIdempotencyKey());
        }
        merged.setNumbers(extracted.getNumbers() == null ? List.of() : extracted.getNumbers());
        return merged;
    }

    private boolean shouldFillTitle(
            AgentToolParameters merged,
            List<AgentToolParameterField> awaitingFields,
            AgentToolParameters extracted
    ) {
        return hasText(extracted.getTitle())
                && (!hasText(merged.getTitle()) || awaitingFields.contains(AgentToolParameterField.TITLE));
    }

    private boolean shouldFillDescription(
            AgentToolParameters merged,
            List<AgentToolParameterField> awaitingFields,
            AgentToolParameters extracted,
            String message
    ) {
        return hasText(message)
                && (!hasText(merged.getDescription())
                || awaitingFields.contains(AgentToolParameterField.DESCRIPTION)
                || extracted.getDescription() != null);
    }

    private boolean isLikelyMetadataOnlyMessage(AgentToolParameters extracted, String message) {
        if (!hasText(message)) return false;
        String trimmed = message.trim();
        return trimmed.length() <= 20
                && (extracted.getType() != null || extracted.getCategory() != null || extracted.getPriority() != null)
                && !containsProblemNarrative(trimmed);
    }

    private boolean containsProblemNarrative(String message) {
        return message.contains("无法")
                || message.contains("失败")
                || message.contains("异常")
                || message.contains("报错")
                || message.contains("问题")
                || message.contains("影响")
                || message.toLowerCase().contains("error");
    }

    private String buildCreateClarificationReply(
            List<AgentToolParameterField> missingFields,
            AgentToolParameters draft,
            String message
    ) {
        if (missingFields.isEmpty()) return "请继续补充创建工单所需的信息。";
        if (missingFields.size() == 1) {
            AgentToolParameterField field = missingFields.get(0);
            return switch (field) {
                case TITLE -> "请补充工单标题，尽量用一句话说明核心问题。";
                case DESCRIPTION -> "请补充更完整的问题描述，例如现象、影响范围和你已尝试过的操作。";
                case CATEGORY -> "请补充工单分类：ACCOUNT、SYSTEM、ENVIRONMENT、OTHER。";
                case PRIORITY -> "请补充工单优先级：LOW、MEDIUM、HIGH、URGENT。";
                default -> "请补充" + field.getLabel() + "。";
            };
        }
        StringBuilder reply = new StringBuilder("我先记录了当前工单草稿");
        if (hasText(draft.getTitle())) {
            reply.append("（标题：").append(draft.getTitle()).append("）");
        }
        reply.append("。还需要补充：");
        for (int i = 0; i < missingFields.size(); i++) {
            if (i > 0) reply.append("、");
            reply.append(missingFields.get(i).getLabel());
        }
        reply.append("。消息内容：").append(message == null ? "" : message.trim());
        return reply.toString();
    }

    private AgentToolParameters copyParameters(AgentToolParameters source) {
        return AgentToolParameters.builder()
                .ticketId(source.getTicketId())
                .assigneeId(source.getAssigneeId())
                .title(source.getTitle())
                .description(source.getDescription())
                .idempotencyKey(source.getIdempotencyKey())
                .type(source.getType())
                .category(source.getCategory())
                .priority(source.getPriority())
                .summaryRequested(source.getSummaryRequested())
                .summaryView(source.getSummaryView())
                .numbers(source.getNumbers() == null ? List.of() : new ArrayList<>(source.getNumbers()))
                .build();
    }

    private boolean isCancelMessage(String message) {
        if (!hasText(message)) return false;
        String normalized = message.trim().toLowerCase();
        return normalized.contains("取消")
                || normalized.contains("不用了")
                || normalized.contains("算了")
                || normalized.equals("cancel");
    }

    private boolean isConfirmMessage(String message) {
        if (!hasText(message)) return false;
        String normalized = message.trim().toLowerCase();
        return normalized.contains("确认")
                || normalized.contains("同意")
                || normalized.contains("执行")
                || normalized.contains("confirm")
                || normalized.equals("yes");
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "未识别" : String.valueOf(value);
    }

    private AgentTool toolForIntent(AgentIntent intent) {
        return skillRegistry.requireByIntent(intent).tool();
    }

    private AgentTool toolForName(String toolName) {
        return skillRegistry.requireByToolName(toolName).tool();
    }

    private AgentChatResult toChatResult(
            String sessionId,
            IntentRoute route,
            AgentSessionContext context,
            AgentToolResult toolResult,
            String reply,
            boolean springAiReady,
            AgentPlan plan,
            AgentTraceContext trace
    ) {
        return AgentChatResult.builder()
                .sessionId(sessionId)
                .intent(route.getIntent().name())
                .reply(hasText(reply) ? reply : (toolResult != null ? toolResult.getReply() : ""))
                .route(route)
                .context(context)
                .result(toolResult != null ? toolResult.getData() : null)
                .springAiChatReady(springAiReady)
                .plan(plan)
                .traceId(trace == null ? null : trace.getTraceId())
                .build();
    }

    private boolean isSpringAiChatReady() {
        return chatEnabled && chatClientProvider.getIfAvailable() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
