package com.smartticket.biz.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartticket.biz.service.dashboard.DashboardService.AgentMetrics;
import com.smartticket.biz.service.dashboard.DashboardService.RagMetrics;
import com.smartticket.biz.service.dashboard.DashboardService.TicketMetrics;
import com.smartticket.domain.mapper.DashboardMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * DashboardService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardMapper dashboardMapper;

    @Test
    void shouldAggregateTicketStatusCounts() {
        when(dashboardMapper.countTicketByStatus()).thenReturn(List.of(
                Map.of("status", "PENDING_ASSIGN", "cnt", 1L),
                Map.of("status", "PROCESSING", "cnt", 3L),
                Map.of("status", "RESOLVED", "cnt", 2L),
                Map.of("status", "CLOSED", "cnt", 10L)
        ));
        when(dashboardMapper.countTicketCreatedToday()).thenReturn(5L);

        DashboardService service = new DashboardService(dashboardMapper, false, "UNKNOWN");
        TicketMetrics metrics = service.aggregateTicket();

        assertEquals(1, metrics.pendingAssignCount);
        assertEquals(3, metrics.processingCount);
        assertEquals(2, metrics.resolvedCount);
        assertEquals(10, metrics.closedCount);
        assertEquals(5, metrics.todayCreatedCount);
    }

    @Test
    void shouldAggregateRagKnowledgeBuildCounts() {
        when(dashboardMapper.countKnowledgeBuildTaskByStatus()).thenReturn(List.of(
                Map.of("status", "SUCCESS", "cnt", 8L),
                Map.of("status", "FAILED", "cnt", 1L),
                Map.of("status", "PENDING", "cnt", 2L)
        ));
        when(dashboardMapper.countKnowledgeEmbedding()).thenReturn(80L);
        when(dashboardMapper.findLatestKnowledgeBuildTime()).thenReturn(LocalDateTime.of(2026, 4, 25, 10, 0));

        DashboardService service = new DashboardService(dashboardMapper, true, "PGVECTOR");
        RagMetrics metrics = service.aggregateRag();

        assertEquals(8, metrics.knowledgeBuildSuccessCount);
        assertEquals(1, metrics.knowledgeBuildFailedCount);
        assertEquals(80, metrics.embeddingChunkCount);
        assertEquals("PGVECTOR", metrics.retrievalPath);
        assertEquals("2026-04-25T10:00", metrics.latestKnowledgeBuildAt);
    }

    @Test
    void shouldReturnDashboardResponse() {
        when(dashboardMapper.countTicketByStatus()).thenReturn(List.of(
                Map.of("status", "PENDING_ASSIGN", "cnt", 1L),
                Map.of("status", "PROCESSING", "cnt", 2L),
                Map.of("status", "RESOLVED", "cnt", 3L),
                Map.of("status", "CLOSED", "cnt", 4L)
        ));
        when(dashboardMapper.countTicketCreatedToday()).thenReturn(5L);
        when(dashboardMapper.countKnowledgeBuildTaskByStatus()).thenReturn(List.of(
                Map.of("status", "SUCCESS", "cnt", 8L)
        ));
        when(dashboardMapper.countKnowledgeEmbedding()).thenReturn(80L);
        when(dashboardMapper.countAgentTraceRecent(any())).thenReturn(20L);
        when(dashboardMapper.countAgentTraceSuccessRecent(any())).thenReturn(18L);
        when(dashboardMapper.avgAgentTraceElapsedRecent(any())).thenReturn(120.0);

        DashboardService service = new DashboardService(dashboardMapper, true, "PGVECTOR");

        TicketMetrics ticket = service.aggregateTicket();
        RagMetrics rag = service.aggregateRag();
        AgentMetrics agent = service.aggregateAgent();

        assertEquals(1, ticket.pendingAssignCount);
        assertEquals(8, rag.knowledgeBuildSuccessCount);
        assertEquals(20, agent.recentAgentCallCount);
        assertEquals(18, agent.recentSuccessCount);
        assertEquals(120.0, agent.avgLatencyMs, 0.001);
    }
}
