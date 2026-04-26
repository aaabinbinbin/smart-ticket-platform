package com.smartticket.api.controller.agent;

import com.smartticket.agent.metrics.AgentTraceMetricsService;
import com.smartticket.agent.trace.AgentTraceService;
import com.smartticket.api.dto.agent.AgentTraceMetricsResponse;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.domain.entity.AgentTraceRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.Authentication;
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

    private final AgentTraceService traceService;
    private final AgentTraceMetricsService metricsService;
    private final CurrentUserResolver currentUserResolver;

    public AgentTraceController(AgentTraceService traceService,
                                AgentTraceMetricsService metricsService,
                                CurrentUserResolver currentUserResolver) {
        this.traceService = traceService;
        this.metricsService = metricsService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/by-session")
    @Operation(summary = "按会话查询智能体轨迹（仅管理员）")
    public ApiResponse<List<AgentTraceRecord>> bySession(@RequestParam String sessionId,
                                                          Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(traceService.findBySessionId(sessionId));
    }

    @GetMapping("/recent-by-user")
    @Operation(summary = "查询用户最近的智能体轨迹")
    public ApiResponse<List<AgentTraceRecord>> recentByUser(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        Long targetUserId = user.isAdmin() ? userId : user.getUserId();
        return ApiResponse.success(traceService.findRecentByUserId(targetUserId, limit));
    }

    @GetMapping("/by-failure")
    @Operation(summary = "按失败类型查询智能体轨迹（仅管理员）")
    public ApiResponse<List<AgentTraceRecord>> byFailure(@RequestParam(required = false) String failureType,
                                                          Authentication authentication) {
        requireAdmin(authentication);
        return ApiResponse.success(traceService.findByFailureType(failureType));
    }

    @GetMapping("/metrics/recent-by-user")
    @Operation(summary = "汇总用户最近的智能体轨迹指标")
    public ApiResponse<AgentTraceMetricsResponse> recentMetricsByUser(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        Long targetUserId = user.isAdmin() ? userId : user.getUserId();
        List<AgentTraceRecord> records = traceService.findRecentByUserId(targetUserId, limit);
        return ApiResponse.success(AgentTraceMetricsResponse.from(metricsService.summarize(records)));
    }

    private void requireAdmin(Authentication authentication) {
        CurrentUser user = currentUserResolver.resolve(authentication);
        if (!user.isAdmin()) {
            throw new BusinessException(BusinessErrorCode.ADMIN_REQUIRED);
        }
    }
}
