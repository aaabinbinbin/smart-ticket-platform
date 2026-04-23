package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.AgentUserPreferenceMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentUserPreferenceMemoryMapper {
    AgentUserPreferenceMemory findByUserId(@Param("userId") Long userId);

    int upsert(AgentUserPreferenceMemory memory);
}
