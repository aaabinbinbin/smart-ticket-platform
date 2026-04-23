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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingAction {
    private AgentIntent pendingIntent;

    private String pendingToolName;

    private AgentToolParameters pendingParameters;

    @Builder.Default
    private List<AgentToolParameterField> awaitingFields = new ArrayList<>();

    private boolean awaitingConfirmation;

    private String confirmationSummary;

    private AgentToolResult lastToolResult;
}
