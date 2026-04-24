package com.smartticket.agent.planner;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体计划步骤类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPlanStep {
    // 阶段
    private AgentPlanStage stage;
    // 动作
    private AgentPlanAction action;
    // 技能编码
    private String skillCode;
    // 摘要
    private String summary;
    // completed
    private boolean completed;
    // completed时间
    private LocalDateTime completedAt;
}
