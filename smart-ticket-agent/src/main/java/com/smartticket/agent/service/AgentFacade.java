package com.smartticket.agent.service;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.support.SpringAiToolCallState;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.agent.tool.ticket.CreateTicketTool;
import com.smartticket.agent.tool.ticket.QueryTicketTool;
import com.smartticket.agent.tool.ticket.SearchHistoryTool;
import com.smartticket.agent.tool.ticket.TransferTicketTool;
import com.smartticket.biz.model.CurrentUser;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 对话门面。
 *
 * <p>api 模块只访问该门面。第一版入口以 Spring AI ChatClient + Tool Calling 为主链路；
 * 当模型未启用、模型没有触发工具或模型调用失败时，回退到同一套 Tool、Guard 和会话上下文，
 * 不再保留旧的自定义 LLM 编排链路。</p>
 */
@Service
public class AgentFacade {
    private static final Logger log = LoggerFactory.getLogger(AgentFacade.class);

    /** Spring AI ChatClient 提供者，未启用模型时可以为空。 */
    private final ObjectProvider<ChatClient> chatClientProvider;

    /** Agent 对真实模型调用的业务开关。 */
    private final boolean chatEnabled;

    /** 规则意图路由器，用于限制本轮只暴露一个业务 Tool。 */
    private final IntentRouter intentRouter;

    /** Agent 会话上下文服务。 */
    private final AgentSessionService sessionService;

    /** Tool 执行前边界守卫，集中做风险、权限前置和必填参数判断。 */
    private final AgentExecutionGuard executionGuard;

    /** 确定性兜底链路使用的浅层参数抽取器。 */
    private final AgentToolParameterExtractor parameterExtractor;

    /** 四个核心 Spring AI Tool Bean，同时实现项目内 AgentTool 接口。 */
    private final QueryTicketTool queryTicketTool;
    private final CreateTicketTool createTicketTool;
    private final TransferTicketTool transferTicketTool;
    private final SearchHistoryTool searchHistoryTool;

    public AgentFacade(
            ObjectProvider<ChatClient> chatClientProvider,
            @Value("${smart-ticket.ai.chat.enabled:false}") boolean chatEnabled,
            IntentRouter intentRouter,
            AgentSessionService sessionService,
            AgentExecutionGuard executionGuard,
            AgentToolParameterExtractor parameterExtractor,
            QueryTicketTool queryTicketTool,
            CreateTicketTool createTicketTool,
            TransferTicketTool transferTicketTool,
            SearchHistoryTool searchHistoryTool
    ) {
        this.chatClientProvider = chatClientProvider;
        this.chatEnabled = chatEnabled;
        this.intentRouter = intentRouter;
        this.sessionService = sessionService;
        this.executionGuard = executionGuard;
        this.parameterExtractor = parameterExtractor;
        this.queryTicketTool = queryTicketTool;
        this.createTicketTool = createTicketTool;
        this.transferTicketTool = transferTicketTool;
        this.searchHistoryTool = searchHistoryTool;
    }

    /**
     * 处理一轮 Agent 对话。
     *
     * @param currentUser 当前登录用户
     * @param sessionId 会话 ID
     * @param message 用户原始消息
     * @return Agent 对话结果
     */
    public AgentChatResult chat(CurrentUser currentUser, String sessionId, String message) {
        boolean springAiReady = isSpringAiChatReady();
        AgentSessionContext context = sessionService.load(sessionId);
        IntentRoute route = intentRouter.route(message, context);
        log.info("agent facade chat: sessionId={}, userId={}, intent={}, springAiChatReady={}",
                sessionId, currentUser.getUserId(), route.getIntent(), springAiReady);

        if (springAiReady) {
            Optional<AgentChatResult> springAiResult =
                    trySpringAiToolCalling(currentUser, sessionId, message, context, route);
            if (springAiResult.isPresent()) {
                return springAiResult.get();
            }
        }
        return executeDeterministicFallback(currentUser, sessionId, message, context, route, springAiReady);
    }

    /**
     * 尝试使用 Spring AI 原生 Tool Calling。
     *
     * <p>本阶段不把所有意图塞进一个超级 prompt，而是先由代码侧路由确定意图，
     * 再只向模型暴露该意图对应的一个 Tool。</p>
     */
    private Optional<AgentChatResult> trySpringAiToolCalling(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return Optional.empty();
        }

        SpringAiToolCallState state = new SpringAiToolCallState();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(SpringAiToolSupport.CURRENT_USER_KEY, currentUser);
        toolContext.put(SpringAiToolSupport.SESSION_CONTEXT_KEY, context);
        toolContext.put(SpringAiToolSupport.MESSAGE_KEY, message);
        toolContext.put(SpringAiToolSupport.ROUTE_KEY, route);
        toolContext.put(SpringAiToolSupport.STATE_KEY, state);

        try {
            String content = chatClient.prompt()
                    .system(systemPrompt(route.getIntent()))
                    .user(userPrompt(message, route))
                    .tools(springAiToolFor(route.getIntent()))
                    .toolContext(toolContext)
                    .call()
                    .content();
            AgentToolResult toolResult = state.getResult();
            if (toolResult == null) {
                log.info("spring ai tool calling produced no tool result, use deterministic fallback: sessionId={}",
                        sessionId);
                return Optional.empty();
            }
            sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
            return Optional.of(toChatResult(sessionId, route, context, toolResult,
                    hasText(content) ? content : toolResult.getReply(), true));
        } catch (RuntimeException ex) {
            log.warn("spring ai tool calling failed, use deterministic fallback: sessionId={}, reason={}",
                    sessionId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 模型不可用时的确定性兜底执行。
     *
     * <p>兜底链路只负责让 P0 闭环可运行：按规则抽取参数，经过 Guard 校验后调用同一个 Tool。
     * 任何写操作仍然只能通过 Tool 内部的 biz service 完成。</p>
     */
    private AgentChatResult executeDeterministicFallback(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            boolean springAiReady
    ) {
        AgentTool tool = agentToolFor(route.getIntent());
        AgentToolParameters parameters = parameterExtractor.extract(message, context);
        sessionService.resolveReferences(message, context, parameters);
        ToolCallPlan plan = ToolCallPlan.builder()
                .intent(route.getIntent())
                .toolName(tool.name())
                .parameters(parameters)
                .llmGenerated(false)
                .reason("Spring AI ChatClient unavailable or no tool call")
                .build();

        AgentExecutionDecision decision = executionGuard.check(currentUser, message, context, route, plan);
        AgentToolResult toolResult;
        if (decision.isAllowed()) {
            toolResult = decision.getTool().execute(AgentToolRequest.builder()
                    .currentUser(currentUser)
                    .message(message)
                    .context(context)
                    .route(route)
                    .parameters(parameters)
                    .build());
        } else {
            toolResult = decision.toToolResult(tool.name());
        }
        sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady);
    }

    /** 按意图选择本轮唯一允许暴露给 Spring AI 的 Tool Bean。 */
    private Object springAiToolFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> createTicketTool;
            case TRANSFER_TICKET -> transferTicketTool;
            case SEARCH_HISTORY -> searchHistoryTool;
            case QUERY_TICKET -> queryTicketTool;
        };
    }

    /** 按意图选择项目内部 AgentTool，用于确定性兜底执行。 */
    private AgentTool agentToolFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> createTicketTool;
            case TRANSFER_TICKET -> transferTicketTool;
            case SEARCH_HISTORY -> searchHistoryTool;
            case QUERY_TICKET -> queryTicketTool;
        };
    }

    /** 组装统一的 Agent 对外结果。 */
    private AgentChatResult toChatResult(
            String sessionId,
            IntentRoute route,
            AgentSessionContext context,
            AgentToolResult toolResult,
            String reply,
            boolean springAiReady
    ) {
        return AgentChatResult.builder()
                .sessionId(sessionId)
                .intent(route.getIntent().name())
                .reply(hasText(reply) ? reply : toolResult.getReply())
                .route(route)
                .context(context)
                .result(toolResult.getData())
                .springAiChatReady(springAiReady)
                .build();
    }

    /** 按意图生成系统提示词，避免把所有意图塞进一个超级 prompt。 */
    private String systemPrompt(AgentIntent intent) {
        return switch (intent) {
            case QUERY_TICKET -> "你是工单查询助手。本轮只能调用 queryTicket，查询当前工单事实，不得检索历史知识库。";
            case CREATE_TICKET -> "你是工单创建助手。本轮只能调用 createTicket。创建动作必须通过工具完成，不能编造创建结果。";
            case TRANSFER_TICKET -> "你是工单转派助手。本轮只能调用 transferTicket。转派是高风险写操作，必须遵守工具返回的确认或失败信息。";
            case SEARCH_HISTORY -> "你是历史经验检索助手。本轮只能调用 searchHistory。检索结果只作参考，不代表当前工单事实。";
        };
    }

    /** 生成用户提示词，明确本轮路由结果和用户原始输入。 */
    private String userPrompt(String message, IntentRoute route) {
        return """
                本轮意图：%s
                路由原因：%s
                用户消息：%s

                请根据用户消息提取工具参数并调用本轮提供的工具。不要调用未提供的工具。
                """.formatted(route.getIntent().name(), route.getReason(), message);
    }

    /** 判断 Spring AI ChatClient 是否已经可用于真实模型调用。 */
    private boolean isSpringAiChatReady() {
        return chatEnabled && chatClientProvider.getIfAvailable() != null;
    }

    /** 判断字符串是否有有效内容。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
