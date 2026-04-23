package com.smartticket.api.controller.agent;

import com.smartticket.agent.trace.AgentTraceService;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.domain.entity.AgentTraceRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
    public ApiResponse<List<AgentTraceRecord>> recentByUser(@RequestParam Long userId) {
        return ApiResponse.success(traceService.findRecentByUserId(userId, 20));
    }

    @GetMapping("/by-failure")
    @Operation(summary = "Query agent traces by failure type")
    public ApiResponse<List<AgentTraceRecord>> byFailure(@RequestParam(required = false) String failureType) {
        return ApiResponse.success(traceService.findByFailureType(failureType));
    }
}
