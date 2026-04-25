package com.smartticket.agent.metrics;

import com.smartticket.domain.entity.AgentTraceRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Agent trace 指标聚合服务。
 *
 * <p>该服务位于 P8 指标收尾层，只基于已经生成的 AgentTraceRecord 做内存聚合，
 * 为稳定性验收、压测复盘和面试展示提供最近调用质量指标。它不参与 Agent 主链决策，
 * 不执行写操作，也不会修改 session、memory、pendingAction 或 trace。</p>
 */
@Service
public class AgentTraceMetricsService {

    /**
     * 聚合最近调用指标。
     *
     * @param records 待聚合的 trace 记录
     * @return 指标快照；records 为空时返回全零结果
     */
    public AgentTraceMetrics summarize(List<AgentTraceRecord> records) {
        List<AgentTraceRecord> safeRecords = records == null ? List.of() : records;
        return AgentTraceMetrics.builder()
                .total(safeRecords.size())
                .clarifyCount(countClarify(safeRecords))
                .springAiUsedCount(safeRecords.stream().filter(AgentTraceRecord::isSpringAiUsed).count())
                .springAiSuccessCount(countSpringAiSuccess(safeRecords))
                .fallbackCount(safeRecords.stream().filter(AgentTraceRecord::isFallbackUsed).count())
                .degradedCount(countByFailurePrefix(safeRecords, "AGENT_DEGRADED"))
                .failedCount(countFailed(safeRecords))
                .averageElapsedMillis(averageElapsed(safeRecords))
                .p95ElapsedMillis(percentile95(safeRecords))
                .routeDistribution(groupBy(safeRecords, AgentTraceRecord::getIntent))
                .skillUsage(groupBy(safeRecords, AgentTraceRecord::getTriggeredSkill))
                .failureDistribution(groupBy(safeRecords, AgentTraceRecord::getFailureType))
                .build();
    }

    private long countClarify(List<AgentTraceRecord> records) {
        return records.stream()
                .filter(record -> contains(record.getStepJson(), "\"stage\":\"clarify\"")
                        || "WAIT_USER".equals(record.getPlanStage()))
                .count();
    }

    private long countSpringAiSuccess(List<AgentTraceRecord> records) {
        return records.stream()
                .filter(record -> record.isSpringAiUsed() && !record.isFallbackUsed() && !"FAILED".equals(record.getStatus()))
                .count();
    }

    private long countFailed(List<AgentTraceRecord> records) {
        return records.stream()
                .filter(record -> "FAILED".equals(record.getStatus()) || hasText(record.getFailureType()))
                .count();
    }

    private long countByFailurePrefix(List<AgentTraceRecord> records, String prefix) {
        return records.stream()
                .filter(record -> record.getFailureType() != null && record.getFailureType().startsWith(prefix))
                .count();
    }

    private double averageElapsed(List<AgentTraceRecord> records) {
        return records.stream()
                .mapToLong(AgentTraceRecord::getElapsedMillis)
                .average()
                .orElse(0.0d);
    }

    private long percentile95(List<AgentTraceRecord> records) {
        if (records.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = records.stream()
                .map(AgentTraceRecord::getElapsedMillis)
                .sorted(Comparator.naturalOrder())
                .toList();
        // P95 使用向上取整定位，避免样本量较小时低估尾延迟。
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95d) - 1);
        return sorted.get(index);
    }

    private Map<String, Long> groupBy(List<AgentTraceRecord> records, Function<AgentTraceRecord, String> classifier) {
        return records.stream()
                .map(classifier)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    private boolean contains(String value, String pattern) {
        return value != null && value.contains(pattern);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
