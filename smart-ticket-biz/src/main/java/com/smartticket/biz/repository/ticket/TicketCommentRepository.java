package com.smartticket.biz.repository.ticket;

import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.mapper.TicketCommentMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单评论仓储封装。
 */
@Repository
public class TicketCommentRepository {
    private final TicketCommentMapper ticketCommentMapper;

    public TicketCommentRepository(TicketCommentMapper ticketCommentMapper) {
        this.ticketCommentMapper = ticketCommentMapper;
    }

    public int insert(TicketComment comment) {
        return ticketCommentMapper.insert(comment);
    }

    public TicketComment findById(Long id) {
        return ticketCommentMapper.findById(id);
    }

    public List<TicketComment> findByTicketId(Long ticketId) {
        return ticketCommentMapper.findByTicketId(ticketId);
    }
}

