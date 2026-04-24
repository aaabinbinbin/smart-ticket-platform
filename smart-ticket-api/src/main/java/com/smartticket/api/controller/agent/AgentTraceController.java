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

/**
 * 智能体执行轨迹查询与轻量指标统计接口。
 */
@RestController
@RequestMapping("/api/agent/traces")
@Tag(name = "智能体轨迹", description = "智能体执行轨迹查询")
public class AgentTraceController {
    /**
     * 智能体轨迹服务，用于查询持久化或内存中的执行记录。
     */
    private final AgentTraceService traceService;

    /**
     * 创建智能体轨迹控制器。
     *
     * @param traceService Agent trace 服务
     */
    public AgentTraceController(AgentTraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 按会话 ID 查询该会话下的 Agent 执行记录。
     *
     * @param sessionId 会话 ID
     * @return trace 记录列表
     */
    @GetMapping("/by-session")
    @Operation(summary = "按会话查询智能体轨迹")
    public ApiResponse<List<AgentTraceRecord>> bySession(@RequestParam String sessionId) {
        return ApiResponse.success(traceService.findBySessionId(sessionId));
    }

    /**
     * 查询指定用户最近的 Agent 执行记录。
     *
     * @param userId 用户 ID
     * @param limit 返回数量上限
     * @return 最近 trace 记录列表
     */
    @GetMapping("/recent-by-user")
    @Operation(summary = "查询用户最近的智能体轨迹")
    public ApiResponse<List<AgentTraceRecord>> recentByUser(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(traceService.findRecentByUserId(userId, limit));
    }

    /**
     * 按失败类型查询 Agent 执行记录。
     *
     * @param failureType 失败类型，允许为空表示查询全部失败分类记录
     * @return trace 记录列表
     */
    @GetMapping("/by-failure")
    @Operation(summary = "按失败类型查询智能体轨迹")
    public ApiResponse<List<AgentTraceRecord>> byFailure(@RequestParam(required = false) String failureType) {
        return ApiResponse.success(traceService.findByFailureType(failureType));
    }

    /**
     * 聚合指定用户最近 N 次 Agent 调用的质量指标。
     *
     * @param userId 用户 ID
     * @param limit 纳入统计的最近记录数量
     * @return Agent 调用质量指标
     */
    @GetMapping("/metrics/recent-by-user")
    @Operation(summary = "汇总用户最近的智能体轨迹指标")
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

    /**
     * 统计触发澄清或等待用户输入的记录数量。
     *
     * @param records trace 记录列表
     * @return 澄清次数
     */
    private long countClarify(List<AgentTraceRecord> records) {
        return records.stream()
                .filter(record -> contains(record.getStepJson(), "\"stage\":\"clarify\"")
                        || "WAIT_USER".equals(record.getPlanStage()))
                .count();
    }

    /**
     * 按指定字段对 trace 记录做计数聚合。
     *
     * @param records trace 记录列表
     * @param classifier 字段提取函数
     * @return 字段值到次数的映射
     */
    private Map<String, Long> groupBy(
            List<AgentTraceRecord> records,
            java.util.function.Function<AgentTraceRecord, String> classifier
    ) {
        return records.stream()
                .map(classifier)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    /**
     * 判断字符串中是否包含指定片段。
     *
     * @param value 原始字符串
     * @param pattern 待匹配片段
     * @return 是否包含
     */
    private boolean contains(String value, String pattern) {
        return value != null && value.contains(pattern);
    }
}
