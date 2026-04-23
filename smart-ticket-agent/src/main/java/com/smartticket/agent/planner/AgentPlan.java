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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlan {
    private String goal;
    private AgentIntent intent;
    private AgentPlanStage currentStage;
    private AgentPlanAction nextAction;
    private String nextSkillCode;
    private ToolRiskLevel riskLevel;
    private boolean waitingForUser;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<AgentToolParameterField> requiredSlots = new ArrayList<>();

    @Builder.Default
    private List<AgentPlanStep> completedSteps = new ArrayList<>();

    @Builder.Default
    private List<AgentPlanStep> plannedSteps = new ArrayList<>();
}
