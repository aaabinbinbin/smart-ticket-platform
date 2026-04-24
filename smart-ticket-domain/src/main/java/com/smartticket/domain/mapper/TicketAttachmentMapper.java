package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketAttachment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单附件 Mapper，对应表 {@code ticket_attachment}。
 */
@Mapper
public interface TicketAttachmentMapper {

    /**
     * 处理新增。
     */
    int insert(TicketAttachment ticketAttachment);

    /**
     * 查询按ID。
     */
    TicketAttachment findById(@Param("id") Long id);

    /**
     * 查询按工单ID。
     */
    List<TicketAttachment> findByTicketId(@Param("ticketId") Long ticketId);
}
