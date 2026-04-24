package com.smartticket.agent.planner;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体计划类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlan {
    // goal
    private String goal;
    // 意图
    private AgentIntent intent;
    // 当前阶段
    private AgentPlanStage currentStage;
    // next动作
    private AgentPlanAction nextAction;
    // next技能编码
    private String nextSkillCode;
    // riskLevel
    private ToolRiskLevel riskLevel;
    // waitingFor用户
    private boolean waitingForUser;
    // 更新时间
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<AgentToolParameterField> requiredSlots = new ArrayList<>();

    @Builder.Default
    private List<AgentPlanStep> completedSteps = new ArrayList<>();

}
