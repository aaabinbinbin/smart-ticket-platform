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
    // 工单命令服务
    private final TicketCommandService ticketCommandService;
    // 工单查询服务
    private final TicketQueryService ticketQueryService;
    // 工单流转服务
    private final TicketWorkflowService ticketWorkflowService;
    // 工单评论服务
    private final TicketCommentService ticketCommentService;
    // 工单队列绑定服务
    private final TicketQueueBindingService ticketQueueBindingService;
    // 工单审批服务
    private final TicketApprovalService ticketApprovalService;

    /**
     * 构造工单服务。
     */
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

    /**
     * 创建工单。
     */
    public Ticket createTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        return ticketCommandService.createTicket(operator, command);
    }

    /**
     * 获取详情。
     */
    public TicketDetailDTO getDetail(CurrentUser operator, Long ticketId) {
        return ticketQueryService.getDetail(operator, ticketId);
    }

    /**
     * 获取摘要。
     */
    public TicketSummaryDTO getSummary(CurrentUser operator, Long ticketId, TicketSummaryViewEnum requestedView) {
        return ticketQueryService.getSummary(operator, ticketId, requestedView);
    }

    /**
     * 分页查询工单。
     */
    public PageResult<Ticket> pageTickets(CurrentUser operator, TicketPageQueryDTO query) {
        return ticketQueryService.pageTickets(operator, query);
    }

    /**
     * 获取审批。
     */
    public TicketApproval getApproval(CurrentUser operator, Long ticketId) {
        return ticketApprovalService.getApproval(operator, ticketId);
    }

    /**
     * 提交审批。
     */
    public TicketApproval submitApproval(
            CurrentUser operator,
            Long ticketId,
            Long templateId,
            Long approverId,
            String submitComment
    ) {
        return ticketApprovalService.submitApproval(operator, ticketId, templateId, approverId, submitComment);
    }

    /**
     * 处理approve。
     */
    public TicketApproval approve(CurrentUser operator, Long ticketId, String decisionComment) {
        return ticketApprovalService.approve(operator, ticketId, decisionComment);
    }

    /**
     * 处理reject。
     */
    public TicketApproval reject(CurrentUser operator, Long ticketId, String decisionComment) {
        return ticketApprovalService.reject(operator, ticketId, decisionComment);
    }

    /**
     * 分派工单。
     */
    public Ticket assignTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        return ticketWorkflowService.assignTicket(operator, ticketId, assigneeId);
    }

    /**
     * 认领工单。
     */
    public Ticket claimTicket(CurrentUser operator, Long ticketId) {
        return ticketWorkflowService.claimTicket(operator, ticketId);
    }

    /**
     * 绑定工单队列。
     */
    public Ticket bindTicketQueue(CurrentUser operator, Long ticketId, Long groupId, Long queueId) {
        return ticketQueueBindingService.bindTicketQueue(operator, ticketId, groupId, queueId);
    }

    /**
     * 转派工单。
     */
    public Ticket transferTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        return ticketWorkflowService.transferTicket(operator, ticketId, assigneeId);
    }

    /**
     * 更新状态。
     */
    public Ticket updateStatus(CurrentUser operator, Long ticketId, TicketUpdateStatusCommandDTO command) {
        return ticketWorkflowService.updateStatus(operator, ticketId, command);
    }

    /**
     * 新增评论。
     */
    public TicketComment addComment(CurrentUser operator, Long ticketId, String content) {
        return ticketCommentService.addComment(operator, ticketId, content);
    }

    /**
     * 关闭工单。
     */
    public Ticket closeTicket(CurrentUser operator, Long ticketId) {
        return ticketWorkflowService.closeTicket(operator, ticketId);
    }
}
