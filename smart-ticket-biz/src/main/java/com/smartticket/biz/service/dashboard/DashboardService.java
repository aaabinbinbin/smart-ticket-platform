package com.smartticket.biz.service.dashboard;

import com.smartticket.domain.mapper.DashboardMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 管理端 Dashboard 服务。
 *
 * <p>聚合工单、RAG、Agent 三大板块指标，统计数据来源于数据库聚合查询。</p>
 */
@Service
public class DashboardService {
    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final DashboardMapper dashboardMapper;
    private final boolean vectorStoreEnabled;
    private final String retrievalPath;

    public DashboardService(
            DashboardMapper dashboardMapper,
            @Value("${smart-ticket.ai.vector-store.enabled:false}") boolean vectorStoreEnabled,
            @Value("${smart-ticket.ai.vector-store.retrieval-path:UNKNOWN}") String retrievalPath
    ) {
        this.dashboardMapper = dashboardMapper;
        this.vectorStoreEnabled = vectorStoreEnabled;
        this.retrievalPath = retrievalPath;
    }

    /** 工单指标集合。 */
    public static class TicketMetrics {
        public long pendingAssignCount;
        public long processingCount;
        public long resolvedCount;
        public long closedCount;
        public long todayCreatedCount;
    }

    /** RAG 指标集合。 */
    public static class RagMetrics {
        public long knowledgeCount;
        public long knowledgeBuildSuccessCount;
        public long knowledgeBuildFailedCount;
        public long embeddingChunkCount;
        public String latestKnowledgeBuildAt;
        public String retrievalPath;
    }

    /** Agent 指标集合。 */
    public static class AgentMetrics {
        public long recentAgentCallCount;
        public long recentSuccessCount;
        public double avgLatencyMs;
    }

    /** 聚合工单指标。 */
    public TicketMetrics aggregateTicket() {
        long pendingAssign = 0;
        long processing = 0;
        long resolved = 0;
        long closed = 0;

        List<Map<String, Object>> statusCounts = dashboardMapper.countTicketByStatus();
        for (Map<String, Object> row : statusCounts) {
            String status = (String) row.get("status");
            Number cnt = (Number) row.get("cnt");
            long count = cnt == null ? 0 : cnt.longValue();
            switch (status) {
                case "PENDING_ASSIGN" -> pendingAssign = count;
                case "PROCESSING" -> processing = count;
                case "RESOLVED" -> resolved = count;
                case "CLOSED" -> closed = count;
                default -> log.debug("Dashboard 忽略未知工单状态：{}", status);
            }
        }

        long todayCreated = dashboardMapper.countTicketCreatedToday();

        TicketMetrics m = new TicketMetrics();
        m.pendingAssignCount = pendingAssign;
        m.processingCount = processing;
        m.resolvedCount = resolved;
        m.closedCount = closed;
        m.todayCreatedCount = todayCreated;
        return m;
    }

    /** 聚合 RAG 指标。 */
    public RagMetrics aggregateRag() {
        long buildSuccess = 0;
        long buildFailed = 0;

        List<Map<String, Object>> taskStatusCounts = dashboardMapper.countKnowledgeBuildTaskByStatus();
        for (Map<String, Object> row : taskStatusCounts) {
            String status = (String) row.get("status");
            Number cnt = (Number) row.get("cnt");
            long count = cnt == null ? 0 : cnt.longValue();
            switch (status) {
                case "SUCCESS" -> buildSuccess = count;
                case "FAILED", "DEAD" -> buildFailed += count;
                default -> log.debug("Dashboard 忽略未知构建任务状态：{}", status);
            }
        }

        long knowledgeCount = dashboardMapper.countActiveKnowledge();
        long embeddingChunkCount = dashboardMapper.countKnowledgeEmbedding();
        LocalDateTime latestBuildAt = dashboardMapper.findLatestKnowledgeBuildTime();

        RagMetrics m = new RagMetrics();
        m.knowledgeCount = knowledgeCount;
        m.knowledgeBuildSuccessCount = buildSuccess;
        m.knowledgeBuildFailedCount = buildFailed;
        m.embeddingChunkCount = embeddingChunkCount;
        m.latestKnowledgeBuildAt = latestBuildAt == null ? null : latestBuildAt.toString();
        m.retrievalPath = vectorStoreEnabled ? retrievalPath : "MYSQL_FALLBACK";
        return m;
    }

    /** 聚合 Agent 指标。 */
    public AgentMetrics aggregateAgent() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        long callCount = dashboardMapper.countAgentTraceRecent(since);
        long successCount = dashboardMapper.countAgentTraceSuccessRecent(since);
        Double avgElapsed = dashboardMapper.avgAgentTraceElapsedRecent(since);

        AgentMetrics m = new AgentMetrics();
        m.recentAgentCallCount = callCount;
        m.recentSuccessCount = successCount;
        m.avgLatencyMs = avgElapsed == null ? 0.0 : avgElapsed;
        return m;
    }
}
