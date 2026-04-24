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

    /**
     * 处理新增。
     */
    int insert(TicketSlaInstance instance);

    /**
     * 查询按ID。
     */
    TicketSlaInstance findById(@Param("id") Long id);

    /**
     * 查询按工单ID。
     */
    TicketSlaInstance findByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 更新按工单ID。
     */
    int updateByTicketId(TicketSlaInstance instance);

    /**
     * 查询BreachedCandidates。
     */
    List<TicketSlaInstance> findBreachedCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 处理Breached。
     */
    int markBreached(@Param("id") Long id);
}
