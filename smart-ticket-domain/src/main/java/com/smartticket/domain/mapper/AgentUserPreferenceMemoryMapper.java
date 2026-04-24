package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.AgentUserPreferenceMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 智能体用户Preference记忆映射接口定义。
 */
@Mapper
public interface AgentUserPreferenceMemoryMapper {
    /**
     * 查询按用户ID。
     */
    AgentUserPreferenceMemory findByUserId(@Param("userId") Long userId);

    /**
     * 插入或更新数据。
     */
    int upsert(AgentUserPreferenceMemory memory);
}
