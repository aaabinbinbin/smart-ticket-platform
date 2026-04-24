package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.util.List;

/**
 * 从 markdown 文件加载的技能定义，包含 skill 的元数据和工具绑定信息。
 */
public class SkillDefinition {

    private String skillCode;
    private String skillName;
    private String description;
    private List<AgentIntent> supportedIntents;
    private ToolRiskLevel riskLevel;
    private boolean canAutoExecute;
    private String toolBeanName;
    private List<String> requiredPermissions;

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String v) { this.skillCode = v; }

    public String getSkillName() { return skillName; }
    public void setSkillName(String v) { this.skillName = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public List<AgentIntent> getSupportedIntents() { return supportedIntents; }
    public void setSupportedIntents(List<AgentIntent> v) { this.supportedIntents = v; }

    public ToolRiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(ToolRiskLevel v) { this.riskLevel = v; }

    public boolean isCanAutoExecute() { return canAutoExecute; }
    public void setCanAutoExecute(boolean v) { this.canAutoExecute = v; }

    public String getToolBeanName() { return toolBeanName; }
    public void setToolBeanName(String v) { this.toolBeanName = v; }

    public List<String> getRequiredPermissions() { return requiredPermissions; }
    public void setRequiredPermissions(List<String> v) { this.requiredPermissions = v; }
}
