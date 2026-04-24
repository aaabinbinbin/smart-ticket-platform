package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.List;

/**
 * 抽象工具支撑技能类。
 */
public abstract class AbstractToolBackedSkill implements AgentSkill {
    // 工具
    private final AgentTool tool;

    /**
     * 构造抽象工具支撑技能。
     */
    protected AbstractToolBackedSkill(AgentTool tool) {
        this.tool = tool;
    }

    /**
     * 处理Name。
     */
    @Override
    public String skillName() {
        return metadata().getName();
    }

    /**
     * 处理描述。
     */
    @Override
    public String description() {
        return metadata().getDescription();
    }

    /**
     * 处理参数结构。
     */
    @Override
    public List<AgentToolParameterField> inputSchema() {
        return metadata().getRequiredFields();
    }

    /**
     * 处理权限信息。
     */
    @Override
    public List<String> requiredPermissions() {
        return List.of();
    }

    /**
     * 处理等级。
     */
    @Override
    public ToolRiskLevel riskLevel() {
        return metadata().getRiskLevel();
    }

    /**
     * 处理AutoExecute。
     */
    @Override
    public boolean canAutoExecute() {
        return !metadata().isRequireConfirmation();
    }

    /**
     * 处理工具。
     */
    @Override
    public AgentTool tool() {
        return tool;
    }

    /**
     * 处理元数据。
     */
    protected AgentToolMetadata metadata() {
        return tool.metadata();
    }

    /**
     * 处理意图。
     */
    protected List<AgentIntent> intent(AgentIntent intent) {
        return List.of(intent);
    }
}
