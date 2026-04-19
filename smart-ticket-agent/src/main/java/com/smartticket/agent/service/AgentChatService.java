package com.smartticket.agent.service;

import com.smartticket.agent.dto.AgentChatRequestDTO;
import com.smartticket.agent.dto.AgentChatResponseDTO;
import com.smartticket.agent.orchestration.TicketAgentOrchestrator;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.model.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Agent 对话入口服务。
 *
 * <p>阶段九开始，Controller 仍然调用本服务，但真正的单 Agent 编排逻辑已经下沉到
 * TicketAgentOrchestrator。本服务只负责把认证用户转换为 biz 层 CurrentUser，并保持
 * /api/agent/chat 对外契约稳定。</p>
 */
@Service
public class AgentChatService {
    /**
     * 单 Agent 编排服务，负责读取上下文、生成并校验计划、执行 Tool、观察结果和更新上下文。
     */
    private final TicketAgentOrchestrator orchestrator;

    public AgentChatService(TicketAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 处理一次 Agent 对话请求。
     *
     * <p>本方法不再直接编排 Tool，避免入口服务承担过多职责。</p>
     */
    public AgentChatResponseDTO chat(Authentication authentication, AgentChatRequestDTO request) {
        CurrentUser currentUser = currentUser(authentication);
        return orchestrator.chat(currentUser, request);
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
