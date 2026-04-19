package com.smartticket.agent.tool.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 具体 Agent 能力的调用结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolResult {
    private boolean invoked;
    private AgentToolStatus status;
    private String toolName;
    private String reply;
    private Object data;
    private Long activeTicketId;
    private Long activeAssigneeId;
}
