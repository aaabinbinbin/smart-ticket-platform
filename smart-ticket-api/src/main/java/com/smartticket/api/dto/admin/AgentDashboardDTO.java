package com.smartticket.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Dashboard 指标 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDashboardDTO {
    /** 最近 7 天 Agent 调用次数。 */
    private long recentAgentCallCount;
    /** 最近 7 天 Agent 成功次数。 */
    private long recentSuccessCount;
    /** 最近 7 天 Agent 平均耗时（毫秒）。 */
    private double avgLatencyMs;
}
