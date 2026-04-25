package com.smartticket.agent.react;

import com.smartticket.agent.execution.AgentExecutionMode;
import com.smartticket.agent.execution.AgentExecutionPolicy;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanAction;
import com.smartticket.agent.planner.AgentPlanStage;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.prompt.PromptTemplateService;
import com.smartticket.agent.resilience.AgentTurnBudget;
import com.smartticket.agent.resilience.AgentTurnBudgetService;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.support.SpringAiToolCallState;
import com.smartticket.agent.tool.support.SpringAiToolCallState.AgentToolCallRecord;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.agent.trace.AgentTraceContext;
import com.smartticket.agent.trace.AgentTraceService;
import com.smartticket.biz.model.CurrentUser;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 只读 ReAct 执行器。
 *
 * <p>该执行器位于 AgentFacade 的只读查询分支中，专门处理 QUERY_TICKET、SEARCH_HISTORY 这类允许模型辅助推理的场景。
 * 它只负责调用 ChatClient、透传只读工具白名单、收集本轮模型输出与工具调用记录，不直接提交 session/memory/pendingAction。
 * 写操作绝不能经过该执行器；如果当前轮没有可暴露的只读工具或 ChatClient 不可用，应返回 empty 交给上层走确定性降级路径。</p>
 */
@Service
public class ReadOnlyReactExecutor {
    private static final Logger log = LoggerFactory.getLogger(ReadOnlyReactExecutor.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final AgentPlanner agentPlanner;
    private final PromptTemplateService promptTemplateService;
    private final AgentTraceService traceService;
    private final AgentReactToolCatalog agentReactToolCatalog;
    private final AgentTurnBudgetService budgetService;

    public ReadOnlyReactExecutor(
            ObjectProvider<ChatClient> chatClientProvider,
            AgentPlanner agentPlanner,
            PromptTemplateService promptTemplateService,
            AgentTraceService traceService,
            AgentReactToolCatalog agentReactToolCatalog,
            AgentTurnBudgetService budgetService
    ) {
        this.chatClientProvider = chatClientProvider;
        this.agentPlanner = agentPlanner;
        this.promptTemplateService = promptTemplateService;
        this.traceService = traceService;
        this.agentReactToolCatalog = agentReactToolCatalog;
        this.budgetService = budgetService;
    }

    /**
     * 执行当前轮只读 ReAct。
     *
     * <p>该方法会修改 plan 与 trace，用于记录“本轮进入了只读 ReAct 及其工具调用链”；但不会修改
     * session/memory/pendingAction，也不会直接生成最终返回给前端的 AgentChatResult。</p>
     *
     * @param currentUser 当前登录用户
     * @param message 当前轮用户消息
     * @param context 当前会话上下文，只读参与 prompt 组装与 toolContext 透传
     * @param route 当前轮路由结果
     * @param plan 当前轮计划对象，会被标记为进入 ReAct 阶段
     * @param policy 当前轮执行策略，提供只读工具白名单与超时预算
     * @param trace 当前轮 trace 上下文，用于记录 ReAct 结构化步骤
     * @return 执行摘要与工具调用记录；当本轮不适合进入 ReAct 时返回 empty
     */
    public Optional<ReadOnlyReactExecution> execute(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            AgentExecutionPolicy policy,
            AgentTraceContext trace,
            AgentTurnBudget budget
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return Optional.empty();
        }

        Object[] tools = agentReactToolCatalog.buildTools(policy);
        List<String> allowedToolNames = agentReactToolCatalog.allowedToolNames(policy);
        if (tools.length == 0 || allowedToolNames.isEmpty()) {
            // 没有只读工具时必须直接交回确定性查询链，避免模型在“无白名单”状态下自由发挥。
            return Optional.empty();
        }

        SpringAiToolCallState callState = new SpringAiToolCallState();
        Map<String, Object> toolContext = buildToolContext(
                currentUser, message, context, route, callState, allowedToolNames, budget);
        preparePlanAndTrace(plan, trace, route);

        try {
            // LLM 调用预算在调用前扣减，避免模型慢调用已经发出后才发现超限。
            budgetService.consumeLlmCall(budget);
            List<ChatResponse> responses = chatClient.prompt()
                    .system(buildSystemPrompt())
                    .user(buildConversationContext(context, message, route))
                    .tools(tools)
                    .toolContext(toolContext)
                    .stream()
                    .chatResponse()
                    .collectList()
                    .block(resolveTimeout(policy));
            String modelReply = collectStreamingOutput(responses);
            if (hasText(modelReply)) {
                traceService.recordReasoning(trace, modelReply);
            }
            return Optional.of(finishExecution(route, context, plan, trace, callState, modelReply));
        } catch (RuntimeException ex) {
            log.warn("只读 ReAct streaming 调用失败，尝试降级到非流式调用: intent={}, reason={}",
                    route == null ? null : route.getIntent(), ex.getMessage());
            traceService.step(trace, "agent", "react-loop", null, "RETRY_CALL", ex.getMessage());
            return tryNonStreaming(chatClient, message, context, route, plan, policy, trace, tools, toolContext, callState, budget);
        }
    }

    /**
     * 只读 ReAct 执行结果。
     *
     * <p>该结构只承载本轮 ReAct 的执行摘要和工具调用记录，供上层统一提交 session/memory 与渲染 reply。
     * 它本身不具备任何状态变更能力。</p>
     */
    public record ReadOnlyReactExecution(
            AgentExecutionSummary summary,
            List<AgentToolCallRecord> toolCalls
    ) {
    }

    private Optional<ReadOnlyReactExecution> tryNonStreaming(
            ChatClient chatClient,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            AgentExecutionPolicy policy,
            AgentTraceContext trace,
            Object[] tools,
            Map<String, Object> toolContext,
            SpringAiToolCallState callState,
            AgentTurnBudget budget
    ) {
        try {
            // 非流式重试同样属于 LLM 调用，必须受同一轮预算约束。
            budgetService.consumeLlmCall(budget);
            String content = chatClient.prompt()
                    .system(buildSystemPrompt())
                    .user(buildConversationContext(context, message, route))
                    .tools(tools)
                    .toolContext(toolContext)
                    .call()
                    .content();

            // 非流式模式拿不到逐步思考文本，这里保留旧行为，只记录一个结构化占位说明。
            traceService.recordReasoning(trace, "[非流式模式，中间推理过程未记录]");
            return Optional.of(finishExecution(route, context, plan, trace, callState, content));
        } catch (RuntimeException ex) {
            log.warn("只读 ReAct 非流式调用也失败，将交回确定性查询链: intent={}, reason={}",
                    route == null ? null : route.getIntent(), ex.getMessage());
            traceService.step(trace, "agent", "react-loop", null, "FAILED", ex.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> buildToolContext(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            SpringAiToolCallState callState,
            List<String> allowedToolNames,
            AgentTurnBudget budget
    ) {
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(SpringAiToolSupport.CURRENT_USER_KEY, currentUser);
        toolContext.put(SpringAiToolSupport.SESSION_CONTEXT_KEY, context);
        toolContext.put(SpringAiToolSupport.MESSAGE_KEY, message);
        toolContext.put(SpringAiToolSupport.ROUTE_KEY, route);
        toolContext.put(SpringAiToolSupport.STATE_KEY, callState);
        toolContext.put(SpringAiToolSupport.ALLOWED_TOOL_NAMES_KEY, allowedToolNames);
        toolContext.put(SpringAiToolSupport.TURN_BUDGET_KEY, budget);
        return toolContext;
    }

    private void preparePlanAndTrace(AgentPlan plan, AgentTraceContext trace, IntentRoute route) {
        agentPlanner.beforeExecute(plan);
        // 只读 ReAct 仍然是本轮主链的一部分，因此需要把“进入模型推理阶段”显式写进 plan 与 trace。
        plan.setCurrentStage(AgentPlanStage.AGENT_THINKING);
        plan.setNextAction(AgentPlanAction.REACT_REASONING);
        traceService.step(trace, "agent", "react-loop", null, "START",
                route == null || route.getIntent() == null ? null : route.getIntent().name());
    }

    private ReadOnlyReactExecution finishExecution(
            IntentRoute route,
            AgentSessionContext context,
            AgentPlan plan,
            AgentTraceContext trace,
            SpringAiToolCallState callState,
            String modelReply
    ) {
        List<AgentToolCallRecord> allCalls = callState.getAllCalls();
        for (AgentToolCallRecord call : allCalls) {
            traceService.step(trace, "agent", "tool-call", call.getToolName(),
                    call.getResult() != null ? call.getResult().getStatus().name() : "UNKNOWN", "react-step");
        }
        agentPlanner.recordToolCalls(plan, allCalls);

        AgentToolResult lastResult = callState.getResult();
        AgentExecutionSummary summary = AgentExecutionSummary.builder()
                .status(AgentTurnStatus.COMPLETED)
                .mode(AgentExecutionMode.READ_ONLY_REACT)
                .intent(route == null ? AgentIntent.QUERY_TICKET : route.getIntent())
                .primaryResult(lastResult)
                .pendingAction(context == null ? null : context.getPendingAction())
                .modelReply(modelReply)
                .springAiUsed(true)
                .fallbackUsed(false)
                .toolInvoked(lastResult != null && lastResult.isInvoked())
                .build();
        return new ReadOnlyReactExecution(summary, allCalls);
    }

    private String buildSystemPrompt() {
        String base = promptTemplateService.content(
                "agent-readonly-react-prompt",
                "你是企业工单平台中的只读智能助手。本轮只能使用只读工具完成查询、检索和总结，"
                        + "禁止创建、转派、关闭工单或做任何会修改数据库状态的操作。"
                        + "如果现有信息不足，请基于工具结果说明不足之处，不要臆造事实。"
        );
        String historyRef = promptTemplateService.content("history-summary", "");
        String resultGuide = promptTemplateService.content("result-explanation", "");
        StringBuilder builder = new StringBuilder(base);
        if (hasText(historyRef)) {
            builder.append("\n\n【searchHistory 工具说明】\n").append(historyRef);
        }
        if (hasText(resultGuide)) {
            builder.append("\n\n【回复风格】\n").append(resultGuide);
        }
        return builder.toString();
    }

    private String buildConversationContext(AgentSessionContext context, String message, IntentRoute route) {
        StringBuilder builder = new StringBuilder();
        List<String> recentMessages = context == null ? null : context.getRecentMessages();
        if (recentMessages != null && !recentMessages.isEmpty()) {
            builder.append("## 对话历史\n");
            for (String recentMessage : recentMessages) {
                builder.append(recentMessage).append("\n");
            }
            builder.append("\n");
        }

        builder.append("## 当前用户消息\n").append(message).append("\n\n");
        builder.append("## 系统上下文\n");
        builder.append("系统识别意图：").append(route.getIntent().name());
        builder.append("（置信度：").append(String.format("%.0f%%", route.getConfidence() * 100)).append("）\n");
        builder.append("识别依据：").append(route.getReason()).append("\n");
        if (context != null && context.getActiveTicketId() != null) {
            builder.append("当前活跃工单 ID：").append(context.getActiveTicketId()).append("\n");
        }
        if (context != null
                && context.getWorkingMemory() != null
                && context.getWorkingMemory().getLastToolSummary() != null) {
            builder.append("上次操作摘要：").append(context.getWorkingMemory().getLastToolSummary()).append("\n");
        }
        return builder.toString();
    }

    private String collectStreamingOutput(List<ChatResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "";
        }
        StringBuilder reasoningBuffer = new StringBuilder();
        for (ChatResponse response : responses) {
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                continue;
            }
            String text = response.getResult().getOutput().getText();
            if (hasText(text)) {
                reasoningBuffer.append(text);
            }
        }
        return reasoningBuffer.toString().trim();
    }

    private Duration resolveTimeout(AgentExecutionPolicy policy) {
        return policy == null || policy.getTimeout() == null ? DEFAULT_TIMEOUT : policy.getTimeout();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
