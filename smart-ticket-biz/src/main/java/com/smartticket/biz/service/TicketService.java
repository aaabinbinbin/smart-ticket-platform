package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.dto.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketCommentRepository;
import com.smartticket.biz.repository.TicketOperationLogRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class TicketService {
    private final TicketCommandService ticketCommandService;
    private final TicketQueryService ticketQueryService;
    private final TicketWorkflowService ticketWorkflowService;
    private final TicketCommentService ticketCommentService;
    private final TicketQueueBindingService ticketQueueBindingService;

    public TicketService(
            TicketCommandService ticketCommandService,
            TicketQueryService ticketQueryService,
            TicketWorkflowService ticketWorkflowService,
            TicketCommentService ticketCommentService,
            TicketQueueBindingService ticketQueueBindingService
    ) {
        this.ticketCommandService = ticketCommandService;
        this.ticketQueryService = ticketQueryService;
        this.ticketWorkflowService = ticketWorkflowService;
        this.ticketCommentService = ticketCommentService;
        this.ticketQueueBindingService = ticketQueueBindingService;
    }

    TicketService(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketOperationLogRepository operationLogRepository,
            TicketPermissionService permissionService,
            TicketDetailCacheService ticketDetailCacheService,
            TicketIdempotencyService ticketIdempotencyService,
            TicketSlaService ticketSlaService,
            TicketGroupService ticketGroupService,
            TicketQueueService ticketQueueService,
            TicketQueueMemberService ticketQueueMemberService,
            ApplicationEventPublisher eventPublisher
    ) {
        TicketServiceSupport support = new TicketServiceSupport(
                ticketRepository,
                ticketCommentRepository,
                operationLogRepository,
                permissionService,
                ticketDetailCacheService,
                ticketIdempotencyService,
                ticketSlaService,
                ticketGroupService,
                ticketQueueService,
                eventPublisher
        );
        this.ticketCommandService = new TicketCommandService(support);
        this.ticketQueryService = new TicketQueryService(support);
        this.ticketWorkflowService = new TicketWorkflowService(support, ticketQueueMemberService);
        this.ticketCommentService = new TicketCommentService(support);
        this.ticketQueueBindingService = new TicketQueueBindingService(support);
    }

    public Ticket createTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        return ticketCommandService.createTicket(operator, command);
    }

    public TicketDetailDTO getDetail(CurrentUser operator, Long ticketId) {
        return ticketQueryService.getDetail(operator, ticketId);
    }

    public PageResult<Ticket> pageTickets(CurrentUser operator, TicketPageQueryDTO query) {
        return ticketQueryService.pageTickets(operator, query);
    }

    public Ticket assignTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        return ticketWorkflowService.assignTicket(operator, ticketId, assigneeId);
    }

    public Ticket claimTicket(CurrentUser operator, Long ticketId) {
        return ticketWorkflowService.claimTicket(operator, ticketId);
    }

    public Ticket bindTicketQueue(CurrentUser operator, Long ticketId, Long groupId, Long queueId) {
        return ticketQueueBindingService.bindTicketQueue(operator, ticketId, groupId, queueId);
    }

    public Ticket transferTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        return ticketWorkflowService.transferTicket(operator, ticketId, assigneeId);
    }

    public Ticket updateStatus(CurrentUser operator, Long ticketId, TicketUpdateStatusCommandDTO command) {
        return ticketWorkflowService.updateStatus(operator, ticketId, command);
    }

    public TicketComment addComment(CurrentUser operator, Long ticketId, String content) {
        return ticketCommentService.addComment(operator, ticketId, content);
    }

    public Ticket closeTicket(CurrentUser operator, Long ticketId) {
        return ticketWorkflowService.closeTicket(operator, ticketId);
    }
}
