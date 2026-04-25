package com.smartticket.api.dto.agent;

import com.smartticket.agent.metrics.AgentTraceMetrics;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 最近调用质量指标响应。
 *
 * <p>该 DTO 位于 API 展示层，用于承载 P8 trace metrics 聚合结果。它只描述最近调用的质量、
 * 降级、失败和耗时分布，不参与 Agent 主链，不执行写操作，也不会修改 session、memory、pendingAction 或 trace。</p>
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
     * 降级完成的次数。
     */
    private long degradedCount;

    /**
     * 失败次数。
     */
    private long failedCount;

    /**
     * 平均耗时，单位毫秒。
     */
    private double averageElapsedMillis;

    /**
     * P95 耗时，单位毫秒。
     */
    private long p95ElapsedMillis;

    /**
     * 按意图统计的路由分布。
     */
    private Map<String, Long> routeDistribution;

    /**
     * 按技能编码统计的使用次数。
     */
    private Map<String, Long> skillUsage;

    /**
     * 按失败类型统计的分布。
     */
    private Map<String, Long> failureDistribution;

    /**
     * 从 agent 模块指标模型转换为 HTTP 响应 DTO。
     *
     * <p>该方法只做响应转换，不参与 Agent 主链，不执行写操作，也不会修改
     * session、memory、pendingAction 或 trace。</p>
     *
     * @param metrics agent 模块聚合出的指标
     * @return HTTP 响应 DTO
     */
    public static AgentTraceMetricsResponse from(AgentTraceMetrics metrics) {
        return AgentTraceMetricsResponse.builder()
                .total(metrics.getTotal())
                .clarifyCount(metrics.getClarifyCount())
                .springAiUsedCount(metrics.getSpringAiUsedCount())
                .springAiSuccessCount(metrics.getSpringAiSuccessCount())
                .fallbackCount(metrics.getFallbackCount())
                .degradedCount(metrics.getDegradedCount())
                .failedCount(metrics.getFailedCount())
                .averageElapsedMillis(metrics.getAverageElapsedMillis())
                .p95ElapsedMillis(metrics.getP95ElapsedMillis())
                .routeDistribution(metrics.getRouteDistribution())
                .skillUsage(metrics.getSkillUsage())
                .failureDistribution(metrics.getFailureDistribution())
                .build();
    }
}
