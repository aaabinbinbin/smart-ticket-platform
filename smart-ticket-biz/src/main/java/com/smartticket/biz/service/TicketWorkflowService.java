package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketWorkflowService {
    private final TicketServiceSupport support;
    private final TicketQueueMemberService ticketQueueMemberService;

    public TicketWorkflowService(TicketServiceSupport support, TicketQueueMemberService ticketQueueMemberService) {
        this.support = support;
        this.ticketQueueMemberService = ticketQueueMemberService;
    }

    @Transactional
    public Ticket assignTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        support.permissionService().requireAdmin(operator);
        Ticket before = support.requireTicket(ticketId);
        support.requireStatus(before, TicketStatusEnum.PENDING_ASSIGN, "只有待分配工单可以执行分配");
        support.requireStaffUser(assigneeId);

        support.requireUpdated(support.ticketRepository().updateAssigneeAndStatus(
                ticketId,
                assigneeId,
                before.getStatus(),
                TicketStatusEnum.PROCESSING
        ));
        Ticket after = support.requireTicket(ticketId);
        support.ticketSlaService().createOrRefreshInstance(after);
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.ASSIGN, "分配工单", support.snapshot(before), support.snapshot(after));
        support.ticketDetailCacheService().evict(ticketId);
        return after;
    }

    @Transactional
    public Ticket claimTicket(CurrentUser operator, Long ticketId) {
        Ticket before = support.requireTicket(ticketId);
        support.requireStatus(before, TicketStatusEnum.PENDING_ASSIGN, "只有待分配工单可以认领");
        if (before.getQueueId() == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_CLAIM, "工单尚未绑定队列");
        }
        if (!operator.isAdmin()) {
            support.requireStaffUser(operator.getUserId());
            boolean isQueueMember = ticketQueueMemberService.isEnabledMember(before.getQueueId(), operator.getUserId());
            boolean isGroupOwner = isGroupOwner(before, operator.getUserId());
            if (!isQueueMember && !isGroupOwner) {
                throw new BusinessException(BusinessErrorCode.TICKET_CLAIM_FORBIDDEN);
            }
        }

        support.requireUpdated(support.ticketRepository().updateAssigneeAndStatus(
                ticketId,
                operator.getUserId(),
                before.getStatus(),
                TicketStatusEnum.PROCESSING
        ));
        Ticket after = support.requireTicket(ticketId);
        support.ticketSlaService().createOrRefreshInstance(after);
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.CLAIM, "认领工单", support.snapshot(before), support.snapshot(after));
        support.ticketDetailCacheService().evict(ticketId);
        return after;
    }

    @Transactional
    public Ticket transferTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        Ticket before = support.requireTicket(ticketId);
        support.permissionService().requireTransfer(operator, before);
        support.requireStatus(before, TicketStatusEnum.PROCESSING, "只有处理中工单可以转派");
        support.requireStaffUser(assigneeId);

        support.requireUpdated(support.ticketRepository().updateAssignee(ticketId, assigneeId, before.getStatus()));
        Ticket after = support.requireTicket(ticketId);
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.TRANSFER, "转派工单", support.snapshot(before), support.snapshot(after));
        support.ticketDetailCacheService().evict(ticketId);
        return after;
    }

    @Transactional
    public Ticket updateStatus(CurrentUser operator, Long ticketId, TicketUpdateStatusCommandDTO command) {
        Ticket before = support.requireTicket(ticketId);
        TicketStatusEnum targetStatus = command.getTargetStatus();
        if (targetStatus == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATUS_REQUIRED);
        }
        if (targetStatus == TicketStatusEnum.CLOSED) {
            throw new BusinessException(BusinessErrorCode.CLOSE_TICKET_USE_CLOSE_API);
        }
        validateStatusTransition(operator, before, targetStatus);

        String solutionSummary = command.getSolutionSummary() == null
                ? before.getSolutionSummary()
                : command.getSolutionSummary();
        support.requireUpdated(support.ticketRepository().updateStatus(ticketId, before.getStatus(), targetStatus, solutionSummary));
        Ticket after = support.requireTicket(ticketId);
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.UPDATE_STATUS, "更新工单状态", support.snapshot(before), support.snapshot(after));
        support.ticketDetailCacheService().evict(ticketId);
        return after;
    }

    @Transactional
    public Ticket closeTicket(CurrentUser operator, Long ticketId) {
        Ticket before = support.requireTicket(ticketId);
        support.permissionService().requireClose(operator, before);
        support.requireStatus(before, TicketStatusEnum.RESOLVED, "只有已解决工单可以关闭");

        support.requireUpdated(support.ticketRepository().updateStatus(
                ticketId,
                before.getStatus(),
                TicketStatusEnum.CLOSED,
                before.getSolutionSummary()
        ));
        Ticket after = support.requireTicket(ticketId);
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.CLOSE, "关闭工单", support.snapshot(before), support.snapshot(after));
        support.ticketDetailCacheService().evict(ticketId);
        support.publishTicketClosedAfterCommit(ticketId);
        return after;
    }

    private void validateStatusTransition(CurrentUser operator, Ticket ticket, TicketStatusEnum targetStatus) {
        TicketStatusEnum current = ticket.getStatus();
        if (current == targetStatus) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATUS_UNCHANGED);
        }

        if (current == TicketStatusEnum.PENDING_ASSIGN && targetStatus == TicketStatusEnum.PROCESSING) {
            support.permissionService().requireAdmin(operator);
            if (ticket.getAssigneeId() == null) {
                throw new BusinessException(BusinessErrorCode.TICKET_ASSIGNEE_REQUIRED);
            }
            return;
        }

        if (current == TicketStatusEnum.PROCESSING && targetStatus == TicketStatusEnum.RESOLVED) {
            support.permissionService().requireResolve(operator, ticket);
            return;
        }

        if (current == TicketStatusEnum.RESOLVED && targetStatus == TicketStatusEnum.CLOSED) {
            support.permissionService().requireClose(operator, ticket);
            return;
        }

        throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS_TRANSITION);
    }

    private boolean isGroupOwner(Ticket ticket, Long userId) {
        if (ticket.getGroupId() == null) {
            return false;
        }
        TicketGroup group = support.ticketGroupService().get(ticket.getGroupId());
        return userId.equals(group.getOwnerUserId());
    }
}
