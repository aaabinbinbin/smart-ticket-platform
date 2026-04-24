package com.smartticket.agent.model;

import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体Pending动作类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingAction {
    // pending意图
    private AgentIntent pendingIntent;

    // pending工具Name
    private String pendingToolName;

    // pending参数
    private AgentToolParameters pendingParameters;

    @Builder.Default
    private List<AgentToolParameterField> awaitingFields = new ArrayList<>();

    // awaitingConfirmation
    private boolean awaitingConfirmation;

    // confirmation摘要
    private String confirmationSummary;

    // last工具结果
    private AgentToolResult lastToolResult;
}
