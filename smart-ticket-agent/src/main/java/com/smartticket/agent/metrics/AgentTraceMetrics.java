package com.smartticket.agent.metrics;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Agent 最近调用质量指标。
 *
 * <p>该模型位于 P8 可观测性收尾层，用于把 trace 记录聚合成稳定性验收和压测复盘所需的统计结果。
 * 它只承载统计值，不执行写操作，也不会修改 session、memory、pendingAction 或 trace。</p>
 */
@Data
@Builder
public class AgentTraceMetrics {
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
     * Spring AI 完成且未进入 fallback 的次数。
     */
    private long springAiSuccessCount;

    /**
     * 进入确定性 fallback 链路的次数。
     */
    private long fallbackCount;

    /**
     * 被标记为降级的次数。
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
}
