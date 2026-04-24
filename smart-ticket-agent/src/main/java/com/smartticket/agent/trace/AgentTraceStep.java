package com.smartticket.agent.trace;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体轨迹步骤类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceStep {
    // 阶段
    private String stage;
    // 动作
    private String action;
    // 技能Or工具
    private String skillOrTool;
    // 状态
    private String status;
    // 详情
    private String detail;
    // occurred时间
    private LocalDateTime occurredAt;
}
