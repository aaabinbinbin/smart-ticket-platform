package com.smartticket.agent.service;

import com.smartticket.agent.dto.AgentChatRequestDTO;
import com.smartticket.agent.dto.AgentChatResponseDTO;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRegistry;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
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
 * 最小单轮 Agent 编排服务。
 */
@Service
public class AgentChatService {
    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);
    private static final int MAX_RECENT_MESSAGES = 10;

    private final AgentSessionCacheService sessionCacheService;
    private final IntentRouter intentRouter;
    private final AgentToolRegistry toolRegistry;
    private final AgentToolParameterExtractor parameterExtractor;

    public AgentChatService(
            AgentSessionCacheService sessionCacheService,
            IntentRouter intentRouter,
            AgentToolRegistry toolRegistry,
            AgentToolParameterExtractor parameterExtractor
    ) {
        this.sessionCacheService = sessionCacheService;
        this.intentRouter = intentRouter;
        this.toolRegistry = toolRegistry;
        this.parameterExtractor = parameterExtractor;
    }

    public AgentChatResponseDTO chat(Authentication authentication, AgentChatRequestDTO request) {
        CurrentUser currentUser = currentUser(authentication);
        AgentSessionContext context = sessionCacheService.get(request.getSessionId());
        IntentRoute route = intentRouter.route(request.getMessage(), context);
        log.info("agent route result: sessionId={}, userId={}, route={}", request.getSessionId(),
                currentUser.getUserId(), route);
        log.info("agent session context before call: sessionId={}, context={}", request.getSessionId(), context);

        AgentTool tool = toolRegistry.requireByIntent(route.getIntent());
        AgentToolParameters parameters = parameterExtractor.extract(request.getMessage(), context);
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
