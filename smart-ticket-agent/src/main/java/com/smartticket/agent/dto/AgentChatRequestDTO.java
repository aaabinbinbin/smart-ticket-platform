package com.smartticket.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 对话请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatRequestDTO {
    @NotBlank(message = "sessionId must not be blank")
    @Size(max = 128, message = "sessionId length must not exceed 128")
    private String sessionId;

    @NotBlank(message = "message must not be blank")
    @Size(max = 2000, message = "message length must not exceed 2000")
    private String message;
}
