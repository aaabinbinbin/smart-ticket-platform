package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.biz.dto.ticket.TicketDetailDTO;
import com.smartticket.biz.dto.ticket.TicketPageQueryDTO;
import com.smartticket.biz.dto.ticket.TicketSummaryDTO;
import com.smartticket.biz.dto.ticket.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.approval.TicketApprovalService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import org.springframework.stereotype.Service;

/**
 * 工单子域门面。
 *
 * <p>面向 API 和 Agent 层暴露稳定入口，内部再委派给 command、query、workflow、
 * comment、approval 等细分服务。</p>
 */
@Service
public class TicketService {
    private final TicketCommandService ticketCommandService;
    private final TicketQueryService ticketQueryService;
    private final TicketWorkflowService ticketWorkflowService;
    private final TicketCommentService ticketCommentService;
    private final TicketQueueBindingService ticketQueueBindingService;
    private final TicketApprovalService ticketApprovalService;

    public TicketService(
            TicketCommandService ticketCommandService,
            TicketQueryService ticketQueryService,
            TicketWorkflowService ticketWorkflowService,
            TicketCommentService ticketCommentService,
            TicketQueueBindingService ticketQueueBindingService,
            TicketApprovalService ticketApprovalService
    ) {
        this.ticketCommandService = ticketCommandService;
        this.ticketQueryService = ticketQueryService;
        this.ticketWorkflowService = ticketWorkflowService;
        this.ticketCommentService = ticketCommentService;
        this.ticketQueueBindingService = ticketQueueBindingService;
        this.ticketApprovalService = ticketApprovalService;
    }

    public Ticket createTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        return ticketCommandService.createTicket(operator, command);
    }

    public TicketDetailDTO getDetail(CurrentUser operator, Long ticketId) {
        return ticketQueryService.getDetail(operator, ticketId);
    }

    public TicketSummaryDTO getSummary(CurrentUser operator, Long ticketId, TicketSummaryViewEnum requestedView) {
        return ticketQueryService.getSummary(operator, ticketId, requestedView);
    }

    public PageResult<Ticket> pageTickets(CurrentUser operator, TicketPageQueryDTO query) {
        return ticketQueryService.pageTickets(operator, query);
    }

    public TicketApproval getApproval(CurrentUser operator, Long ticketId) {
        return ticketApprovalService.getApproval(operator, ticketId);
    }

    public TicketApproval submitApproval(
            CurrentUser operator,
            Long ticketId,
            Long templateId,
            Long approverId,
            String submitComment
    ) {
        return ticketApprovalService.submitApproval(operator, ticketId, templateId, approverId, submitComment);
    }

    public TicketApproval approve(CurrentUser operator, Long ticketId, String decisionComment) {
        return ticketApprovalService.approve(operator, ticketId, decisionComment);
    }

    public TicketApproval reject(CurrentUser operator, Long ticketId, String decisionComment) {
        return ticketApprovalService.reject(operator, ticketId, decisionComment);
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
