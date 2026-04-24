package com.smartticket.biz.service.ticket;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.ticket.TicketCommentRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单评论服务。
 */
@Service
public class TicketCommentService {
    // 支撑
    private final TicketServiceSupport support;
    // 工单评论仓储
    private final TicketCommentRepository ticketCommentRepository;
    // 工单详情缓存服务
    private final TicketDetailCacheService ticketDetailCacheService;

    /**
     * 构造工单评论服务。
     */
    public TicketCommentService(
            TicketServiceSupport support,
            TicketCommentRepository ticketCommentRepository,
            TicketDetailCacheService ticketDetailCacheService
    ) {
        this.support = support;
        this.ticketCommentRepository = ticketCommentRepository;
        this.ticketDetailCacheService = ticketDetailCacheService;
    }

    /**
     * 新增评论。
     */
    @Transactional
    public TicketComment addComment(CurrentUser operator, Long ticketId, String content) {
        Ticket ticket = support.requireVisibleTicket(operator, ticketId);
        if (ticket.getStatus() == TicketStatusEnum.CLOSED) {
            throw new BusinessException(BusinessErrorCode.TICKET_CLOSED);
        }

        TicketComment comment = TicketComment.builder()
                .ticketId(ticketId)
                .commenterId(operator.getUserId())
                .commentType("USER_REPLY")
                .content(content)
                .build();
        ticketCommentRepository.insert(comment);
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.COMMENT, "添加工单评论", null, "content=" + content);
        ticketDetailCacheService.evict(ticketId);
        return support.requireComment(comment.getId());
    }
}

