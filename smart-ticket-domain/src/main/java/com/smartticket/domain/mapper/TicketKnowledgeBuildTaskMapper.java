package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单知识构建任务映射接口定义。
 */
@Mapper
public interface TicketKnowledgeBuildTaskMapper {
    /**
     * 新增Ignore。
     */
    int insertIgnore(TicketKnowledgeBuildTask task);

    /**
     * 查询按ID。
     */
    TicketKnowledgeBuildTask findById(@Param("id") Long id);

    /**
     * 查询按工单ID。
     */
    TicketKnowledgeBuildTask findByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 查询可分派数据。
     */
    List<TicketKnowledgeBuildTask> findDispatchable(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 处理认领。
     */
    int claim(@Param("id") Long id, @Param("lockedBy") String lockedBy, @Param("now") LocalDateTime now);

    /**
     * 处理Success。
     */
    int markSuccess(@Param("id") Long id);

    /**
     * 处理失败状态。
     */
    int markFailed(
            @Param("id") Long id,
            @Param("lastError") String lastError,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("dead") boolean dead
    );
}
