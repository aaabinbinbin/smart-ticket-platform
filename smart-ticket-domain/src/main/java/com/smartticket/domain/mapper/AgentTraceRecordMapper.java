package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.AgentTraceRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 智能体轨迹Record映射接口定义。
 */
@Mapper
public interface AgentTraceRecordMapper {
    int insert(AgentTraceRecord record);

    /**
     * 查询按会话ID。
     */
    List<AgentTraceRecord> findBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询Recent按用户ID。
     */
    List<AgentTraceRecord> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 按失败类型查询。
     */
    List<AgentTraceRecord> findByFailureType(@Param("failureType") String failureType);
}
