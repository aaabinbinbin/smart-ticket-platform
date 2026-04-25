package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledge;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Dashboard 指标数据访问接口。
 *
 * <p>提供聚合查询方法用于组装管理端 Dashboard，不修改任何数据。</p>
 */
@Mapper
public interface DashboardMapper {

    /**
     * 按状态统计工单数。
     *
     * @return 每个状态及其计数的列表
     */
    List<Map<String, Object>> countTicketByStatus();

    /**
     * 统计今日创建的工单数。
     */
    long countTicketCreatedToday();

    /**
     * 按状态统计知识构建任务数。
     *
     * @return 每个状态及其计数的列表
     */
    List<Map<String, Object>> countKnowledgeBuildTaskByStatus();

    /**
     * 统计 ACTIVE 知识数量。
     */
    long countActiveKnowledge();

    /**
     * 统计知识切片总数。
     */
    long countKnowledgeEmbedding();

    /**
     * 查询最近的知识构建时间。
     */
    LocalDateTime findLatestKnowledgeBuildTime();

    /**
     * 统计最近 N 天的 Agent 调用次数。
     */
    long countAgentTraceRecent(@Param("since") LocalDateTime since);

    /**
     * 统计最近 N 天的 Agent 成功调用次数（status = 'COMPLETED'）。
     */
    long countAgentTraceSuccessRecent(@Param("since") LocalDateTime since);

    /**
     * 查询最近 N 天的 Agent 平均耗时（毫秒）。
     */
    Double avgAgentTraceElapsedRecent(@Param("since") LocalDateTime since);
}
