package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.AgentTraceRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentTraceRecordMapper {
    int insert(AgentTraceRecord record);

    List<AgentTraceRecord> findBySessionId(@Param("sessionId") String sessionId);

    List<AgentTraceRecord> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    List<AgentTraceRecord> findByFailureType(@Param("failureType") String failureType);
}
