package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketComment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单评论 Mapper，对应表 {@code ticket_comment}。
 */
@Mapper
public interface TicketCommentMapper {

    /**
     * 处理新增。
     */
    int insert(TicketComment ticketComment);

    /**
     * 查询按ID。
     */
    TicketComment findById(@Param("id") Long id);

    /**
     * 查询按工单ID。
     */
    List<TicketComment> findByTicketId(@Param("ticketId") Long ticketId);
}
