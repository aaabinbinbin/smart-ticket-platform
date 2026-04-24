package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.List;

/**
 * 由 SkillDefinition 驱动的技能实现，从 markdown 元数据中获取配置，绑定对应 Tool Bean。
 */
public class DefinitionDrivenSkill implements AgentSkill {

    private final SkillDefinition definition;
    private final AgentTool tool;

    public DefinitionDrivenSkill(SkillDefinition definition, AgentTool tool) {
        this.definition = definition;
        this.tool = tool;
    }

    @Override
    public String skillCode() {
        return definition.getSkillCode();
    }

    @Override
    public String skillName() {
        return definition.getSkillName();
    }

    @Override
    public String description() {
        return definition.getDescription();
    }

    @Override
    public List<AgentToolParameterField> inputSchema() {
        return tool.metadata().getRequiredFields();
    }

    @Override
    public List<String> requiredPermissions() {
        return definition.getRequiredPermissions();
    }

    @Override
    public ToolRiskLevel riskLevel() {
        return definition.getRiskLevel();
    }

    @Override
    public boolean canAutoExecute() {
        return definition.isCanAutoExecute();
    }

    @Override
    public List<AgentIntent> supportedIntents() {
        return definition.getSupportedIntents();
    }

    @Override
    public AgentTool tool() {
        return tool;
    }
}
