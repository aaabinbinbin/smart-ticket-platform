package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.List;

public abstract class AbstractToolBackedSkill implements AgentSkill {
    private final AgentTool tool;

    protected AbstractToolBackedSkill(AgentTool tool) {
        this.tool = tool;
    }

    @Override
    public String skillName() {
        return metadata().getName();
    }

    @Override
    public String description() {
        return metadata().getDescription();
    }

    @Override
    public List<AgentToolParameterField> inputSchema() {
        return metadata().getRequiredFields();
    }

    @Override
    public List<String> requiredPermissions() {
        return List.of();
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return metadata().getRiskLevel();
    }

    @Override
    public boolean canAutoExecute() {
        return !metadata().isRequireConfirmation();
    }

    @Override
    public AgentTool tool() {
        return tool;
    }

    protected AgentToolMetadata metadata() {
        return tool.metadata();
    }

    protected List<AgentIntent> intent(AgentIntent intent) {
        return List.of(intent);
    }
}
