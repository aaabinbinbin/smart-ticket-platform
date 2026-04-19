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

    int insert(TicketKnowledge ticketKnowledge);

    TicketKnowledge findById(@Param("id") Long id);

    TicketKnowledge findByTicketId(@Param("ticketId") Long ticketId);

    List<TicketKnowledge> findActive();

    int update(TicketKnowledge ticketKnowledge);
}
