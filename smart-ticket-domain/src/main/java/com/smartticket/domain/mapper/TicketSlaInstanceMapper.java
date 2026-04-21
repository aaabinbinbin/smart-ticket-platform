package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketSlaInstance;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单 SLA 实例 Mapper，只负责数据访问。
 */
@Mapper
public interface TicketSlaInstanceMapper {

    int insert(TicketSlaInstance instance);

    TicketSlaInstance findById(@Param("id") Long id);

    TicketSlaInstance findByTicketId(@Param("ticketId") Long ticketId);

    int updateByTicketId(TicketSlaInstance instance);

    List<TicketSlaInstance> findBreachedCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int markBreached(@Param("id") Long id);
}
