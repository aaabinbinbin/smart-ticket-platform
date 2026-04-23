package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketKnowledgeBuildTaskMapper {
    int insertIgnore(TicketKnowledgeBuildTask task);

    TicketKnowledgeBuildTask findById(@Param("id") Long id);

    TicketKnowledgeBuildTask findByTicketId(@Param("ticketId") Long ticketId);

    List<TicketKnowledgeBuildTask> findDispatchable(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int claim(@Param("id") Long id, @Param("lockedBy") String lockedBy, @Param("now") LocalDateTime now);

    int markSuccess(@Param("id") Long id);

    int markFailed(
            @Param("id") Long id,
            @Param("lastError") String lastError,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("dead") boolean dead
    );
}
