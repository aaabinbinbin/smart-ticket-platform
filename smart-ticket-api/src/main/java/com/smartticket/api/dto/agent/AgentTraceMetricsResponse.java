package com.smartticket.api.dto.agent;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 最近调用质量指标响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceMetricsResponse {
    /**
     * 纳入统计的 trace 总数。
     */
    private int total;

    /**
     * 触发澄清或等待用户输入的次数。
     */
    private long clarifyCount;

    /**
     * 使用 Spring AI Tool Calling 的次数。
     */
    private long springAiUsedCount;

    /**
     * Spring AI Tool Calling 成功完成且未进入 fallback 的次数。
     */
    private long springAiSuccessCount;

    /**
     * 进入确定性 fallback 链路的次数。
     */
    private long fallbackCount;

    /**
     * 按意图统计的路由分布。
     */
    private Map<String, Long> routeDistribution;

    /**
     * 按技能编码统计的使用次数。
     */
    private Map<String, Long> skillUsage;
}
