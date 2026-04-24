package com.smartticket.agent.skill;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.List;

/**
 * Agent 技能定义，描述一个业务能力的意图、输入、风险和底层 Tool 绑定关系。
 */
public interface AgentSkill {
    /**
     * 返回技能唯一编码。
     *
     * @return 技能编码
     */
    String skillCode();

    /**
     * 返回技能展示名称。
     *
     * @return 技能名称
     */
    String skillName();

    /**
     * 返回技能说明。
     *
     * @return 技能描述
     */
    String description();

    /**
     * 返回技能执行所需的输入字段。
     *
     * @return 输入字段列表
     */
    List<AgentToolParameterField> inputSchema();

    /**
     * 返回执行该技能需要的权限编码。
     *
     * @return 权限编码列表
     */
    List<String> requiredPermissions();

    /**
     * 返回技能风险等级。
     *
     * @return 风险等级
     */
    ToolRiskLevel riskLevel();

    /**
     * 判断该技能是否允许无需人工确认直接执行。
     *
     * @return 是否允许自动执行
     */
    boolean canAutoExecute();

    /**
     * 返回该技能支持的业务意图。
     *
     * @return 支持的意图列表
     */
    List<AgentIntent> supportedIntents();

    /**
     * 返回该技能绑定的底层 Tool。
     *
     * @return 绑定的 Tool
     */
    AgentTool tool();

    /**
     * 判断该技能是否支持指定意图。
     *
     * @param intent 业务意图
     * @return 是否支持
     */
    default boolean supports(AgentIntent intent) {
        return supportedIntents().contains(intent);
    }
}
