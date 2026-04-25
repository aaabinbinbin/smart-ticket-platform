package com.smartticket.api.controller.admin;

import com.smartticket.api.dto.admin.AgentDashboardDTO;
import com.smartticket.api.dto.admin.DashboardResponseDTO;
import com.smartticket.api.dto.admin.RagDashboardDTO;
import com.smartticket.api.dto.admin.TicketDashboardDTO;
import com.smartticket.biz.service.dashboard.DashboardService;
import com.smartticket.biz.service.dashboard.DashboardService.AgentMetrics;
import com.smartticket.biz.service.dashboard.DashboardService.RagMetrics;
import com.smartticket.biz.service.dashboard.DashboardService.TicketMetrics;
import com.smartticket.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 Dashboard 接口。
 *
 * <p>提供工单平台和 RAG/Agent 的核心运行指标，仅 ADMIN 角色可访问。</p>
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理端 Dashboard", description = "工单平台和 RAG/Agent 核心运行指标，仅 ADMIN 可访问")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    /**
     * 创建 Dashboard 控制器。
     *
     * @param dashboardService Dashboard 聚合服务
     */
    public AdminDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 获取 Dashboard 全量指标。
     *
     * @param authentication 当前认证信息
     * @return 工单、RAG、Agent 三大板块指标
     */
    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard 指标", description = "返回工单、RAG、Agent 核心运行指标，仅 ADMIN 可访问")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DashboardResponseDTO> dashboard(Authentication authentication) {
        TicketMetrics ticket = dashboardService.aggregateTicket();
        RagMetrics rag = dashboardService.aggregateRag();
        AgentMetrics agent = dashboardService.aggregateAgent();

        DashboardResponseDTO dto = DashboardResponseDTO.builder()
                .ticket(TicketDashboardDTO.builder()
                        .pendingAssignCount(ticket.pendingAssignCount)
                        .processingCount(ticket.processingCount)
                        .resolvedCount(ticket.resolvedCount)
                        .closedCount(ticket.closedCount)
                        .todayCreatedCount(ticket.todayCreatedCount)
                        .build())
                .rag(RagDashboardDTO.builder()
                        .knowledgeCount(rag.knowledgeCount)
                        .knowledgeBuildSuccessCount(rag.knowledgeBuildSuccessCount)
                        .knowledgeBuildFailedCount(rag.knowledgeBuildFailedCount)
                        .embeddingChunkCount(rag.embeddingChunkCount)
                        .latestKnowledgeBuildAt(rag.latestKnowledgeBuildAt)
                        .retrievalPath(rag.retrievalPath)
                        .build())
                .agent(AgentDashboardDTO.builder()
                        .recentAgentCallCount(agent.recentAgentCallCount)
                        .recentSuccessCount(agent.recentSuccessCount)
                        .avgLatencyMs(agent.avgLatencyMs)
                        .build())
                .build();

        return ApiResponse.success(dto);
    }
}
