package com.smartticket.api.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体对话请求。
 *
 * <p>该对象只承载 HTTP 入参，不包含任何业务规则。具体意图识别、
 * Tool 编排和业务执行由 agent 与 biz 模块处理。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequest {
    /**
     * 会话 ID，用于读取和更新 Agent 短期上下文。
     */
    @NotBlank(message = "sessionId must not be blank")
    @Size(max = 128, message = "sessionId length must not exceed 128")
    private String sessionId;

    /**
     * 用户自然语言消息。
     */
    @NotBlank(message = "message must not be blank")
    @Size(max = 2000, message = "message length must not exceed 2000")
    private String message;
}
