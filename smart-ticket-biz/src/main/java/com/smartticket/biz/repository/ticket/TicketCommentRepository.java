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
    // 工单评论映射接口
    private final TicketCommentMapper ticketCommentMapper;

    /**
     * 构造工单评论仓储。
     */
    public TicketCommentRepository(TicketCommentMapper ticketCommentMapper) {
        this.ticketCommentMapper = ticketCommentMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketComment comment) {
        return ticketCommentMapper.insert(comment);
    }

    /**
     * 查询按ID。
     */
    public TicketComment findById(Long id) {
        return ticketCommentMapper.findById(id);
    }

    /**
     * 查询按工单ID。
     */
    public List<TicketComment> findByTicketId(Long ticketId) {
        return ticketCommentMapper.findByTicketId(ticketId);
    }
}

