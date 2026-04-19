package com.smartticket.agent.service;

import com.smartticket.agent.dto.AgentChatRequestDTO;
import com.smartticket.agent.dto.AgentChatResponseDTO;
import com.smartticket.agent.llm.service.LlmAgentService;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRegistry;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.model.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Agent 对话入口服务。
 *
 * <p>阶段八在阶段七 Tool 层基础上接入 LLM，但仍然保持单链路受控执行：
 * 规则路由提供兜底，LLM 只增强意图识别、参数抽取、缺参澄清和结果总结，
 * 真正业务执行仍然通过 AgentToolRegistry 和 biz 层完成。</p>
 */
@Service
public class AgentChatService {
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);

    /**
     * 会话中保留的最近消息数量，避免上下文无限增长。
     */
    private static final int MAX_RECENT_MESSAGES = 10;

    /**
     * Agent 会话上下文缓存服务。
     */
    private final AgentSessionCacheService sessionCacheService;

    /**
     * 规则版意图路由器，LLM 不可用或输出不可信时作为 fallback。
     */
    private final IntentRouter intentRouter;

    /**
     * Tool 注册表，负责根据 intent 查找可执行 Tool。
     */
    private final AgentToolRegistry toolRegistry;

    /**
     * 规则版参数抽取器，LLM 参数抽取失败时作为 fallback。
     */
    private final AgentToolParameterExtractor parameterExtractor;

    /**
     * LLM 能力服务，负责理解、抽取、澄清和总结，不直接执行业务。
     */
    private final LlmAgentService llmAgentService;

    public AgentChatService(
            AgentSessionCacheService sessionCacheService,
            IntentRouter intentRouter,
            AgentToolRegistry toolRegistry,
            AgentToolParameterExtractor parameterExtractor,
            LlmAgentService llmAgentService
    ) {
        this.sessionCacheService = sessionCacheService;
        this.intentRouter = intentRouter;
        this.toolRegistry = toolRegistry;
        this.parameterExtractor = parameterExtractor;
        this.llmAgentService = llmAgentService;
    }

    /**
     * 处理一次 Agent 对话请求。
     *
     * <p>当前流程先生成规则兜底结果，再尝试使用 LLM 增强，最后统一进入 Tool 执行。</p>
     */
    public AgentChatResponseDTO chat(Authentication authentication, AgentChatRequestDTO request) {
        CurrentUser currentUser = currentUser(authentication);
        AgentSessionContext context = sessionCacheService.get(request.getSessionId());

        // 规则路由先运行，保证 LLM 未配置或失败时仍然能完成基础对话。
        IntentRoute fallbackRoute = intentRouter.route(request.getMessage(), context);
        IntentRoute route = llmAgentService.routeOrFallback(request.getMessage(), context, fallbackRoute);
        log.info("agent route result: sessionId={}, userId={}, route={}", request.getSessionId(),
                currentUser.getUserId(), route);
        log.info("agent session context before call: sessionId={}, context={}", request.getSessionId(), context);

        AgentTool tool = toolRegistry.requireByIntent(route.getIntent());

        // 规则参数作为兜底，LLM 参数只做增强；最终仍由 Tool 校验必填字段。
        AgentToolParameters fallbackParameters = parameterExtractor.extract(request.getMessage(), context);
        AgentToolParameters parameters = llmAgentService.extractParametersOrFallback(
                request.getMessage(), context, route, fallbackParameters);
        AgentToolRequest toolRequest = AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(request.getMessage())
                .context(context)
                .route(route)
                .parameters(parameters)
                .build();
        log.info("agent selected tool: sessionId={}, intent={}, tool={}, metadata={}, parameters={}",
                request.getSessionId(), route.getIntent(), tool.name(), tool.metadata(), parameters);

        AgentToolResult toolResult = tool.execute(toolRequest);
        log.info("agent tool result: sessionId={}, intent={}, tool={}, status={}, invoked={}, result={}",
                request.getSessionId(), route.getIntent(), tool.name(), toolResult.getStatus(),
                toolResult.isInvoked(), toolResult.getData());

        refineReply(request.getMessage(), route, toolResult);

        // Tool 执行成功后更新当前会话指针，便于后续多轮对话引用。
        applyToolResult(context, route, request.getMessage(), toolResult);
        sessionCacheService.save(request.getSessionId(), context);
        log.info("agent session context after call: sessionId={}, context={}", request.getSessionId(), context);

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
     * 使用 LLM 优化回复文本。
     *
     * <p>缺参场景生成澄清问题，其他场景生成结果总结；LLM 失败时保留 Tool 原始回复。</p>
     */
    private void refineReply(String message, IntentRoute route, AgentToolResult toolResult) {
        if (toolResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            toolResult.setReply(llmAgentService.clarifyOrFallback(
                    message,
                    route,
                    missingFields(toolResult.getData()),
                    toolResult.getReply()
            ));
            return;
        }
        toolResult.setReply(llmAgentService.summarizeOrFallback(message, route, toolResult));
    }

    /**
     * 从 ToolResult.data 中提取缺失字段列表。
     *
     * <p>只有 NEED_MORE_INFO 结果会把缺失字段放入 data，其他结果返回空列表。</p>
     */
    private List<AgentToolParameterField> missingFields(Object data) {
        if (!(data instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(AgentToolParameterField.class::isInstance)
                .map(AgentToolParameterField.class::cast)
                .toList();
    }

    /**
     * 根据 Tool 执行结果更新会话上下文。
     *
     * <p>这里只维护 Agent 会话指针和最近消息，不直接写工单事实数据。</p>
     */
    private void applyToolResult(
            AgentSessionContext context,
            IntentRoute route,
            String message,
            AgentToolResult toolResult
    ) {
        context.setLastIntent(route.getIntent().name());
        if (toolResult.getActiveTicketId() != null) {
            context.setActiveTicketId(toolResult.getActiveTicketId());
        }
        if (toolResult.getActiveAssigneeId() != null) {
            context.setActiveAssigneeId(toolResult.getActiveAssigneeId());
        }
        List<String> messages = context.getRecentMessages() == null
                ? new ArrayList<>()
                : new ArrayList<>(context.getRecentMessages());
        messages.add(route.getIntent().name() + ": " + message);
        while (messages.size() > MAX_RECENT_MESSAGES) {
            messages.remove(0);
        }
        context.setRecentMessages(messages);
    }

    /**
     * 将 Spring Security 当前登录用户转换为 biz 层需要的 CurrentUser。
     */
    private CurrentUser currentUser(Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        return CurrentUser.builder()
                .userId(authUser.getUserId())
                .username(authUser.getUsername())
                .roles(authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(authority -> authority.replace("ROLE_", ""))
                        .toList())
                .build();
    }
}
