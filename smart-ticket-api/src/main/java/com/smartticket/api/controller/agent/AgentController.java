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
 * Agent 对话 HTTP 入口，只负责接收请求、解析当前用户并组装响应。
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "智能体对话", description = "智能体对话入口")
public class AgentController {
    /**
     * 智能体主链应用服务。
     */
    private final AgentFacade agentFacade;

    /**
     * 当前登录用户解析器。
     */
    private final CurrentUserResolver currentUserResolver;

    /**
     * 创建智能体对话控制器。
     *
     * @param agentFacade Agent 主链应用服务
     * @param currentUserResolver 当前登录用户解析器
     */
    public AgentController(AgentFacade agentFacade, CurrentUserResolver currentUserResolver) {
        this.agentFacade = agentFacade;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 执行一次 Agent 对话。
     *
     * @param authentication 当前认证信息
     * @param request 对话请求
     * @return Agent 对话响应
     */
    @PostMapping("/chat")
    @Operation(summary = "智能体对话", description = "执行智能体对话与工具编排")
    public ApiResponse<AgentChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody AgentChatRequest request
    ) {
        AgentChatResult result = agentFacade.chat(currentUserResolver.resolve(authentication), request.getSessionId(), request.getMessage());
        return ApiResponse.success(toResponse(result));
    }

    /**
     * 将 agent 模块的应用层结果转换为 HTTP 响应 DTO。
     *
     * @param result Agent 应用层结果
     * @return HTTP 响应 DTO
     */
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
