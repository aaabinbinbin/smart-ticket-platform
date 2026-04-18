package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketAttachment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Ticket attachment mapper for table {@code ticket_attachment}.
 */
@Mapper
public interface TicketAttachmentMapper {

    int insert(TicketAttachment ticketAttachment);

    TicketAttachment findById(@Param("id") Long id);

    List<TicketAttachment> findByTicketId(@Param("ticketId") Long ticketId);
}
