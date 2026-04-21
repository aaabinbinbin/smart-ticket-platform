package com.smartticket.api.controller;

import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.service.AgentFacade;
import com.smartticket.api.dto.agent.AgentChatRequest;
import com.smartticket.api.dto.agent.AgentChatResponse;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent HTTP 接口控制器。
 *
 * <p>api 模块只负责协议适配、参数校验和当前用户转换。自然语言理解、
 * Spring AI ChatClient 接入、Tool 编排和业务执行边界由 agent 模块继续负责。</p>
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent 对话接口", description = "自然语言工单查询、创建、转派和历史经验检索入口")
public class AgentController {
    private final AgentFacade agentFacade;

    public AgentController(AgentFacade agentFacade) {
        this.agentFacade = agentFacade;
    }

    /**
     * Agent 对话入口。
     *
     * @param authentication 当前登录用户
     * @param request 对话请求
     * @return Agent 对话响应
     */
    @PostMapping("/chat")
    @Operation(summary = "Agent 对话", description = "第一版支持 QUERY_TICKET、CREATE_TICKET、TRANSFER_TICKET、SEARCH_HISTORY 四类意图")
    public ApiResponse<AgentChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody AgentChatRequest request
    ) {
        AgentChatResult result = agentFacade.chat(currentUser(authentication), request.getSessionId(), request.getMessage());
        return ApiResponse.success(toResponse(result));
    }

    /**
     * 将 Spring Security 当前用户转换为 biz 层需要的 CurrentUser。
     */
    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }
        if (!(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }
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

    /**
     * 将 agent 模块结果转换成 HTTP 响应模型。
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
                .build();
    }
}
