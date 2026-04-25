package com.smartticket.agent.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartticket.domain.entity.AgentTraceRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentTraceMetricsService} 指标聚合测试。
 *
 * <p>该测试保护 P8 指标口径：最近 trace 可以稳定计算路由分布、失败分布、降级次数和耗时统计，
 * 且统计过程不会反向影响 Agent 主链。</p>
 */
class AgentTraceMetricsServiceTest {

    @Test
    void summarizeShouldAggregateTraceQualityMetrics() {
        AgentTraceMetricsService service = new AgentTraceMetricsService();
        List<AgentTraceRecord> records = List.of(
                record("QUERY_TICKET", "query-ticket", "SUCCESS", null, false, false, 10L, null),
                record("QUERY_TICKET", "query-ticket", "SUCCESS", "AGENT_DEGRADED", false, true, 30L, null),
                record("SEARCH_HISTORY", "search-history", "FAILED", "AGENT_BUDGET_EXCEEDED", true, false, 50L, null),
                record("CREATE_TICKET", "create-ticket", "NEED_MORE_INFO", null, false, false, 20L, "{\"stage\":\"clarify\"}")
        );

        AgentTraceMetrics metrics = service.summarize(records);

        assertEquals(4, metrics.getTotal());
        assertEquals(1, metrics.getClarifyCount());
        assertEquals(1, metrics.getSpringAiUsedCount());
        assertEquals(0, metrics.getSpringAiSuccessCount());
        assertEquals(1, metrics.getFallbackCount());
        assertEquals(1, metrics.getDegradedCount());
        assertEquals(2, metrics.getFailedCount());
        assertEquals(27.5d, metrics.getAverageElapsedMillis());
        assertEquals(50L, metrics.getP95ElapsedMillis());
        assertEquals(2L, metrics.getRouteDistribution().get("QUERY_TICKET"));
        assertEquals(1L, metrics.getFailureDistribution().get("AGENT_BUDGET_EXCEEDED"));
    }

    private AgentTraceRecord record(
            String intent,
            String skill,
            String status,
            String failureType,
            boolean springAiUsed,
            boolean fallbackUsed,
            long elapsed,
            String stepJson
    ) {
        return AgentTraceRecord.builder()
                .intent(intent)
                .triggeredSkill(skill)
                .status(status)
                .failureType(failureType)
                .springAiUsed(springAiUsed)
                .fallbackUsed(fallbackUsed)
                .elapsedMillis(elapsed)
                .stepJson(stepJson)
                .build();
    }
}
