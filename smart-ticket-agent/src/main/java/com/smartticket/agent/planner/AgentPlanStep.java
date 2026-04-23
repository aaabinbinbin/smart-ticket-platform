package com.smartticket.agent.planner;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlanStep {
    private AgentPlanStage stage;
    private AgentPlanAction action;
    private String skillCode;
    private String summary;
    private boolean completed;
    private LocalDateTime completedAt;
}
