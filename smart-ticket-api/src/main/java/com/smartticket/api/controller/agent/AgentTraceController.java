package com.smartticket.api.controller.agent;

import com.smartticket.agent.trace.AgentTraceService;
import com.smartticket.api.dto.agent.AgentTraceMetricsResponse;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.domain.entity.AgentTraceRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/traces")
@Tag(name = "Agent Trace", description = "Agent execution trace query")
public class AgentTraceController {
    private final AgentTraceService traceService;

    public AgentTraceController(AgentTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/by-session")
    @Operation(summary = "Query agent traces by session")
    public ApiResponse<List<AgentTraceRecord>> bySession(@RequestParam String sessionId) {
        return ApiResponse.success(traceService.findBySessionId(sessionId));
    }

    @GetMapping("/recent-by-user")
    @Operation(summary = "Query recent agent traces by user")
    public ApiResponse<List<AgentTraceRecord>> recentByUser(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(traceService.findRecentByUserId(userId, limit));
    }

    @GetMapping("/by-failure")
    @Operation(summary = "Query agent traces by failure type")
    public ApiResponse<List<AgentTraceRecord>> byFailure(@RequestParam(required = false) String failureType) {
        return ApiResponse.success(traceService.findByFailureType(failureType));
    }

    @GetMapping("/metrics/recent-by-user")
    @Operation(summary = "Summarize recent agent trace metrics by user")
    public ApiResponse<AgentTraceMetricsResponse> recentMetricsByUser(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<AgentTraceRecord> records = traceService.findRecentByUserId(userId, limit);
        return ApiResponse.success(AgentTraceMetricsResponse.builder()
                .total(records.size())
                .clarifyCount(countClarify(records))
                .springAiUsedCount(records.stream().filter(AgentTraceRecord::isSpringAiUsed).count())
                .springAiSuccessCount(records.stream()
                        .filter(record -> record.isSpringAiUsed() && !record.isFallbackUsed() && !"FAILED".equals(record.getStatus()))
                        .count())
                .fallbackCount(records.stream().filter(AgentTraceRecord::isFallbackUsed).count())
                .routeDistribution(groupBy(records, AgentTraceRecord::getIntent))
                .skillUsage(groupBy(records, AgentTraceRecord::getTriggeredSkill))
                .build());
    }

    private long countClarify(List<AgentTraceRecord> records) {
        return records.stream()
                .filter(record -> contains(record.getStepJson(), "\"stage\":\"clarify\"")
                        || "WAIT_USER".equals(record.getPlanStage()))
                .count();
    }

    private Map<String, Long> groupBy(
            List<AgentTraceRecord> records,
            java.util.function.Function<AgentTraceRecord, String> classifier
    ) {
        return records.stream()
                .map(classifier)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    private boolean contains(String value, String pattern) {
        return value != null && value.contains(pattern);
    }
}
