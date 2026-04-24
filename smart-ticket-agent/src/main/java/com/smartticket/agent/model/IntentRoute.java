package com.smartticket.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图路由决策结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentRoute {
    // 意图
    private AgentIntent intent;
    // confidence
    private double confidence;
    // reason
    private String reason;
}
