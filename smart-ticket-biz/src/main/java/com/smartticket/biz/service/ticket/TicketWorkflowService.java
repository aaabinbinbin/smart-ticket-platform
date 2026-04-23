package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.approval.TicketApprovalService;
import com.smartticket.biz.service.assignment.TicketGroupService;
import com.smartticket.biz.service.assignment.TicketQueueMemberService;
import com.smartticket.biz.service.sla.TicketSlaService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责工单流转相关的写操作，包括分派、认领、转派、状态推进和关闭。
 * 这里聚焦流程编排，具体的权限、缓存、SLA 和审批约束交给协作者处理。
 */
@Service
public class TicketWorkflowService {
    private final TicketServiceSupport support;
    private final TicketRepository ticketRepository;
    private final TicketDetailCacheService ticketDetailCacheService;
    private final TicketSlaService ticketSlaService;
    private final TicketGroupService ticketGroupService;
    private final TicketQueueMemberService ticketQueueMemberService;
    private final TicketApprovalService ticketApprovalService;

    public TicketWorkflowService(
            TicketServiceSupport support,
            TicketRepository ticketRepository,
            TicketDetailCacheService ticketDetailCacheService,
            TicketSlaService ticketSlaService,
            TicketGroupService ticketGroupService,
            TicketQueueMemberService ticketQueueMemberService,
            TicketApprovalService ticketApprovalService
    ) {
        this.support = support;
        this.ticketRepository = ticketRepository;
        this.ticketDetailCacheService = ticketDetailCacheService;
        this.ticketSlaService = ticketSlaService;
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueMemberService = ticketQueueMemberService;
        this.ticketApprovalService = ticketApprovalService;
    }

    @Transactional
    public Ticket assignTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        support.permissionService().requireAdmin(operator);
        Ticket before = support.requireTicket(ticketId);
        requirePendingAssign(before, "只有待分配工单可以执行分派");
        ticketApprovalService.requireApprovalPassed(before);
        support.requireStaffUser(assigneeId);
        return updateAssigneeAndReload(
                operator,
                before,
                assigneeId,
                TicketStatusEnum.PROCESSING,
                OperationTypeEnum.ASSIGN,
                "分配工单",
                true
        );
    }

    @Transactional
    public Ticket claimTicket(CurrentUser operator, Long ticketId) {
        Ticket before = support.requireTicket(ticketId);
        requirePendingAssign(before, "只有待分配工单可以认领");
        ticketApprovalService.requireApprovalPassed(before);
        ensureOperatorCanClaim(operator, before);
        return updateAssigneeAndReload(
                operator,
                before,
                operator.getUserId(),
                TicketStatusEnum.PROCESSING,
                OperationTypeEnum.CLAIM,
                "认领工单",
                true
        );
    }

    @Transactional
    public Ticket transferTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        Ticket before = support.requireTicket(ticketId);
        support.permissionService().requireTransfer(operator, before);
        requireProcessing(before, "只有处理中工单可以转派");
        ticketApprovalService.requireApprovalPassed(before);
        support.requireStaffUser(assigneeId);
        return transferAssigneeAndReload(operator, before, assigneeId);
    }

    @Transactional
    public Ticket updateStatus(CurrentUser operator, Long ticketId, TicketUpdateStatusCommandDTO command) {
        Ticket before = support.requireTicket(ticketId);
        ticketApprovalService.requireApprovalPassed(before);
        TicketStatusEnum targetStatus = command.getTargetStatus();
        if (targetStatus == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATUS_REQUIRED);
        }
        if (targetStatus == TicketStatusEnum.CLOSED) {
            throw new BusinessException(BusinessErrorCode.CLOSE_TICKET_USE_CLOSE_API);
        }
        validateStatusTransition(operator, before, targetStatus);
        return updateStatusAndReload(operator, before, targetStatus, resolveSolutionSummary(before, command));
    }

    @Transactional
    public Ticket closeTicket(CurrentUser operator, Long ticketId) {
        Ticket before = support.requireTicket(ticketId);
        support.permissionService().requireClose(operator, before);
        requireResolved(before, "只有已解决工单可以关闭");
        ticketApprovalService.requireApprovalPassed(before);
        Ticket after = updateStatusAndReload(
                operator,
                before,
                TicketStatusEnum.CLOSED,
                before.getSolutionSummary(),
                OperationTypeEnum.CLOSE,
                "关闭工单"
        );
        support.publishTicketClosedAfterCommit(ticketId);
        return after;
    }

    private Ticket updateAssigneeAndReload(
            CurrentUser operator,
            Ticket before,
            Long assigneeId,
            TicketStatusEnum targetStatus,
            OperationTypeEnum operationType,
            String actionName,
            boolean refreshSla
    ) {
        support.requireUpdated(ticketRepository.updateAssigneeAndStatus(
                before.getId(),
                assigneeId,
                before.getStatus(),
                targetStatus
        ));
        Ticket after = support.requireTicket(before.getId());
        if (refreshSla) {
            ticketSlaService.createOrRefreshInstance(after);
        }
        return finalizeWorkflowChange(operator, before, after, operationType, actionName);
    }

    private Ticket transferAssigneeAndReload(CurrentUser operator, Ticket before, Long assigneeId) {
        support.requireUpdated(ticketRepository.updateAssignee(before.getId(), assigneeId, before.getStatus()));
        Ticket after = support.requireTicket(before.getId());
        return finalizeWorkflowChange(operator, before, after, OperationTypeEnum.TRANSFER, "转派工单");
    }

    private Ticket updateStatusAndReload(
            CurrentUser operator,
            Ticket before,
            TicketStatusEnum targetStatus,
            String solutionSummary
    ) {
        return updateStatusAndReload(operator, before, targetStatus, solutionSummary, OperationTypeEnum.UPDATE_STATUS, "更新工单状态");
    }

    private Ticket updateStatusAndReload(
            CurrentUser operator,
            Ticket before,
            TicketStatusEnum targetStatus,
            String solutionSummary,
            OperationTypeEnum operationType,
            String actionName
    ) {
        support.requireUpdated(ticketRepository.updateStatus(
                before.getId(),
                before.getStatus(),
                targetStatus,
                solutionSummary
        ));
        Ticket after = support.requireTicket(before.getId());
        return finalizeWorkflowChange(operator, before, after, operationType, actionName);
    }

    private Ticket finalizeWorkflowChange(
            CurrentUser operator,
            Ticket before,
            Ticket after,
            OperationTypeEnum operationType,
            String actionName
    ) {
        support.writeLog(before.getId(), operator.getUserId(), operationType, actionName, support.snapshot(before), support.snapshot(after));
        ticketDetailCacheService.evict(before.getId());
        return after;
    }

    private void ensureOperatorCanClaim(CurrentUser operator, Ticket ticket) {
        if (ticket.getQueueId() == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_CLAIM, "工单尚未绑定队列");
        }
        if (operator.isAdmin()) {
            return;
        }
        support.requireStaffUser(operator.getUserId());
        boolean isQueueMember = ticketQueueMemberService.isEnabledMember(ticket.getQueueId(), operator.getUserId());
        boolean isGroupOwner = !isQueueMember && isGroupOwner(ticket, operator.getUserId());
        if (!isQueueMember && !isGroupOwner) {
            throw new BusinessException(BusinessErrorCode.TICKET_CLAIM_FORBIDDEN);
        }
    }

    private void requirePendingAssign(Ticket ticket, String message) {
        support.requireStatus(ticket, TicketStatusEnum.PENDING_ASSIGN, message);
    }

    private void requireProcessing(Ticket ticket, String message) {
        support.requireStatus(ticket, TicketStatusEnum.PROCESSING, message);
    }

    private void requireResolved(Ticket ticket, String message) {
        support.requireStatus(ticket, TicketStatusEnum.RESOLVED, message);
    }

    private String resolveSolutionSummary(Ticket before, TicketUpdateStatusCommandDTO command) {
        return command.getSolutionSummary() == null ? before.getSolutionSummary() : command.getSolutionSummary();
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
        TicketGroup group = ticketGroupService.get(ticket.getGroupId());
        return userId.equals(group.getOwnerUserId());
    }
}
