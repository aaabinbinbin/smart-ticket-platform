package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Ticket knowledge mapper for table {@code ticket_knowledge}.
 */
@Mapper
public interface TicketKnowledgeMapper {

    int insert(TicketKnowledge ticketKnowledge);

    TicketKnowledge findById(@Param("id") Long id);

    TicketKnowledge findByTicketId(@Param("ticketId") Long ticketId);

    int update(TicketKnowledge ticketKnowledge);
}
