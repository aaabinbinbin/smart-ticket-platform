package com.smartticket.agent.service;

import com.smartticket.agent.dto.AgentChatRequestDTO;
import com.smartticket.agent.dto.AgentChatResponseDTO;
import com.smartticket.agent.llm.config.AgentLlmProperties;
import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.TicketAgentOrchestrator;
import com.smartticket.agent.tool.core.AgentToolResult;
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
import org.springframework.stereotype.Service;

/**
 * Agent 对话门面。
 *
 * <p>该门面是 api 模块访问 agent 模块的统一入口。本阶段优先尝试 Spring AI
 * Tool Calling；当 Spring AI 未启用、模型未调用工具或调用失败时，回退到现有
 * {@link TicketAgentOrchestrator} 受控编排链路。</p>
 */
@Service
public class AgentFacade {
    private static final Logger log = LoggerFactory.getLogger(AgentFacade.class);

    /**
     * 既有单 Agent 编排器，作为 Spring AI Tool Calling 失败时的稳定回退链路。
     */
    private final TicketAgentOrchestrator orchestrator;

    /**
     * Spring AI ChatClient 提供者。
     */
    private final ObjectProvider<ChatClient> chatClientProvider;

    /**
     * Agent 对真实模型调用的业务开关。
     */
    private final AgentLlmProperties llmProperties;

    /**
     * 规则意图路由器，用于按意图只暴露本轮允许的单个 Spring AI Tool。
     */
    private final IntentRouter intentRouter;

    /**
     * Agent 会话上下文服务。
     */
    private final AgentSessionService sessionService;

    /**
     * 四个核心 Spring AI Tool Bean。
     */
    private final QueryTicketTool queryTicketTool;
    private final CreateTicketTool createTicketTool;
    private final TransferTicketTool transferTicketTool;
    private final SearchHistoryTool searchHistoryTool;

    public AgentFacade(
            TicketAgentOrchestrator orchestrator,
            ObjectProvider<ChatClient> chatClientProvider,
            AgentLlmProperties llmProperties,
            IntentRouter intentRouter,
            AgentSessionService sessionService,
            QueryTicketTool queryTicketTool,
            CreateTicketTool createTicketTool,
            TransferTicketTool transferTicketTool,
            SearchHistoryTool searchHistoryTool
    ) {
        this.orchestrator = orchestrator;
        this.chatClientProvider = chatClientProvider;
        this.llmProperties = llmProperties;
        this.intentRouter = intentRouter;
        this.sessionService = sessionService;
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
        log.info("agent facade chat: sessionId={}, userId={}, springAiChatReady={}",
                sessionId, currentUser.getUserId(), springAiReady);
        if (springAiReady) {
            Optional<AgentChatResult> springAiResult = trySpringAiToolCalling(currentUser, sessionId, message, context);
            if (springAiResult.isPresent()) {
                return springAiResult.get();
            }
        }
        return fallbackToOrchestrator(currentUser, sessionId, message, springAiReady);
    }

    /**
     * 尝试使用 Spring AI 原生 Tool Calling。
     *
     * <p>本阶段不会把所有意图塞进一个超级 prompt，而是先用规则路由确定意图，
     * 再只向模型暴露该意图对应的一个 Tool。</p>
     */
    private Optional<AgentChatResult> trySpringAiToolCalling(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return Optional.empty();
        }
        IntentRoute route = intentRouter.route(message, context);
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
                    .tools(toolFor(route.getIntent()))
                    .toolContext(toolContext)
                    .call()
                    .content();
            AgentToolResult toolResult = state.getResult();
            if (toolResult == null) {
                log.info("spring ai tool calling produced no tool result, fallback to orchestrator: sessionId={}", sessionId);
                return Optional.empty();
            }
            sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
            return Optional.of(AgentChatResult.builder()
                    .sessionId(sessionId)
                    .intent(route.getIntent().name())
                    .reply(hasText(content) ? content : toolResult.getReply())
                    .route(route)
                    .context(context)
                    .result(toolResult.getData())
                    .springAiChatReady(true)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("spring ai tool calling failed, fallback to orchestrator: sessionId={}, reason={}",
                    sessionId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 回退到现有受控编排器。
     */
    private AgentChatResult fallbackToOrchestrator(
            CurrentUser currentUser,
            String sessionId,
            String message,
            boolean springAiReady
    ) {
        AgentChatResponseDTO response = orchestrator.chat(currentUser, AgentChatRequestDTO.builder()
                .sessionId(sessionId)
                .message(message)
                .build());
        return AgentChatResult.builder()
                .sessionId(response.getSessionId())
                .intent(response.getIntent())
                .reply(response.getReply())
                .route(response.getRoute())
                .context(response.getContext())
                .result(response.getResult())
                .springAiChatReady(springAiReady)
                .build();
    }

    /**
     * 按意图选择本轮唯一允许暴露给 Spring AI 的 Tool Bean。
     */
    private Object toolFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> createTicketTool;
            case TRANSFER_TICKET -> transferTicketTool;
            case SEARCH_HISTORY -> searchHistoryTool;
            case QUERY_TICKET -> queryTicketTool;
        };
    }

    /**
     * 按意图生成系统提示词，避免把所有意图塞进一个超级 prompt。
     */
    private String systemPrompt(AgentIntent intent) {
        return switch (intent) {
            case QUERY_TICKET -> "你是工单查询助手。本轮只能调用 queryTicket，查询当前工单事实，不得检索历史知识库。";
            case CREATE_TICKET -> "你是工单创建助手。本轮只能调用 createTicket。创建动作必须通过工具完成，不能编造创建结果。";
            case TRANSFER_TICKET -> "你是工单转派助手。本轮只能调用 transferTicket。转派是高风险写操作，必须遵守工具返回的确认或失败信息。";
            case SEARCH_HISTORY -> "你是历史经验检索助手。本轮只能调用 searchHistory。检索结果只作参考，不代表当前工单事实。";
        };
    }

    /**
     * 生成用户提示词，明确本轮路由结果和用户原始输入。
     */
    private String userPrompt(String message, IntentRoute route) {
        return """
                本轮意图：%s
                路由原因：%s
                用户消息：%s

                请根据用户消息提取工具参数并调用本轮提供的工具。不要调用未提供的工具。
                """.formatted(route.getIntent().name(), route.getReason(), message);
    }

    /**
     * 判断 Spring AI ChatClient 是否已经可用于真实模型调用。
     */
    private boolean isSpringAiChatReady() {
        return llmProperties.isEffectiveEnabled() && chatClientProvider.getIfAvailable() != null;
    }

    /**
     * 判断字符串是否有有效内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
