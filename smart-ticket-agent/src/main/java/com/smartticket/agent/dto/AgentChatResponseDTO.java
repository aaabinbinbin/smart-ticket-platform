package com.smartticket.agent.dto;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 对话响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponseDTO {
    private String sessionId;
    private String intent;
    private String reply;
    private IntentRoute route;
    private AgentSessionContext context;
    private Object result;
}
