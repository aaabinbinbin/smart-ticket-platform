package com.smartticket.agent.controller;

import com.smartticket.agent.dto.AgentChatRequestDTO;
import com.smartticket.agent.dto.AgentChatResponseDTO;
import com.smartticket.agent.service.AgentChatService;
import com.smartticket.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对话 HTTP 入口。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {
    private final AgentChatService agentChatService;

    public AgentChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponseDTO> chat(
            Authentication authentication,
            @Valid @RequestBody AgentChatRequestDTO request
    ) {
        return ApiResponse.success(agentChatService.chat(authentication, request));
    }
}
