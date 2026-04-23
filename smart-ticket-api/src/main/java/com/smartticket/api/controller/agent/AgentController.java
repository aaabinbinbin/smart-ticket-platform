package com.smartticket.api.controller.agent;

import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.service.AgentFacade;
import com.smartticket.api.dto.agent.AgentChatRequest;
import com.smartticket.api.dto.agent.AgentChatResponse;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对话 HTTP 入口。
 * 这里只负责接收请求和组装响应，会话编排由 agent facade 承担。
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent Chat", description = "Agent chat entry")
public class AgentController {
    private final AgentFacade agentFacade;
    private final CurrentUserResolver currentUserResolver;

    public AgentController(AgentFacade agentFacade, CurrentUserResolver currentUserResolver) {
        this.agentFacade = agentFacade;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat with agent", description = "Run agent chat and tool orchestration")
    public ApiResponse<AgentChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody AgentChatRequest request
    ) {
        AgentChatResult result = agentFacade.chat(currentUserResolver.resolve(authentication), request.getSessionId(), request.getMessage());
        return ApiResponse.success(toResponse(result));
    }

    private AgentChatResponse toResponse(AgentChatResult result) {
        return AgentChatResponse.builder()
                .sessionId(result.getSessionId())
                .intent(result.getIntent())
                .reply(result.getReply())
                .route(result.getRoute())
                .context(result.getContext())
                .result(result.getResult())
                .springAiChatReady(result.isSpringAiChatReady())
                .plan(result.getPlan())
                .traceId(result.getTraceId())
                .build();
    }
}
