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
    // invoked
    private boolean invoked;
    // 状态
    private AgentToolStatus status;
    // 工具Name
    private String toolName;
    // reply
    private String reply;
    // data
    private Object data;
    // active工单ID
    private Long activeTicketId;
    // active处理人ID
    private Long activeAssigneeId;
}
