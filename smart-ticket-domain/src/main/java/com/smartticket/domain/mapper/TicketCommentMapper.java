package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketComment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Ticket comment mapper for table {@code ticket_comment}.
 */
@Mapper
public interface TicketCommentMapper {

    int insert(TicketComment ticketComment);

    TicketComment findById(@Param("id") Long id);

    List<TicketComment> findByTicketId(@Param("ticketId") Long ticketId);
}
