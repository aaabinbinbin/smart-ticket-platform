package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.List;

public interface AgentSkill {
    String skillCode();

    String skillName();

    String description();

    List<AgentToolParameterField> inputSchema();

    List<String> requiredPermissions();

    ToolRiskLevel riskLevel();

    boolean canAutoExecute();

    List<AgentIntent> supportedIntents();

    AgentTool tool();

    default boolean supports(AgentIntent intent) {
        return supportedIntents().contains(intent);
    }
}
