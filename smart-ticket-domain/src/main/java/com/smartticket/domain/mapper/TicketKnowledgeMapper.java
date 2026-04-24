package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledge;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单知识 Mapper，对应表 {@code ticket_knowledge}。
 */
@Mapper
public interface TicketKnowledgeMapper {

    /**
     * 处理新增。
     */
    int insert(TicketKnowledge ticketKnowledge);

    /**
     * 查询按ID。
     */
    TicketKnowledge findById(@Param("id") Long id);

    /**
     * 查询按工单ID。
     */
    TicketKnowledge findByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 查询启用数据。
     */
    List<TicketKnowledge> findActive();

    /**
     * 更新。
     */
    int update(TicketKnowledge ticketKnowledge);
}
