package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.knowledge.TicketKnowledgeBuildTaskService;
import com.smartticket.biz.service.approval.TicketApprovalService;
import com.smartticket.biz.service.assignment.TicketGroupService;
import com.smartticket.biz.service.assignment.TicketQueueMemberService;
import com.smartticket.biz.service.sla.TicketSlaService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketStatusTransition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责工单流转相关的写操作，包括分派、认领、转派、状态推进和关闭。
 * 这里聚焦流程编排，具体的权限、缓存、SLA 和审批约束交给协作者处理。
 */
@Service
public class TicketWorkflowService {
    // 支撑
    private final TicketServiceSupport support;
    // 工单仓储
    private final TicketRepository ticketRepository;
    // 工单详情缓存服务
    private final TicketDetailCacheService ticketDetailCacheService;
    // 工单SLA服务
    private final TicketSlaService ticketSlaService;
    // 工单分组服务
    private final TicketGroupService ticketGroupService;
    // 工单队列成员服务
    private final TicketQueueMemberService ticketQueueMemberService;
    // 工单审批服务
    private final TicketApprovalService ticketApprovalService;
    // 知识构建任务服务
    private final TicketKnowledgeBuildTaskService knowledgeBuildTaskService;

    /**
     * 构造工单流转服务。
     */
    public TicketWorkflowService(
            TicketServiceSupport support,
            TicketRepository ticketRepository,
            TicketDetailCacheService ticketDetailCacheService,
            TicketSlaService ticketSlaService,
            TicketGroupService ticketGroupService,
            TicketQueueMemberService ticketQueueMemberService,
            TicketApprovalService ticketApprovalService,
            TicketKnowledgeBuildTaskService knowledgeBuildTaskService
    ) {
        this.support = support;
        this.ticketRepository = ticketRepository;
        this.ticketDetailCacheService = ticketDetailCacheService;
        this.ticketSlaService = ticketSlaService;
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueMemberService = ticketQueueMemberService;
        this.ticketApprovalService = ticketApprovalService;
        this.knowledgeBuildTaskService = knowledgeBuildTaskService;
    }

    /**
     * 分派工单。
     */
    @Transactional
    public Ticket assignTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        support.permissionService().requireAdmin(operator);
        Ticket before = support.requireTicket(ticketId);
        // 分派只允许发生在待分配阶段，避免覆盖处理中工单的负责人。
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

    /**
     * 认领工单。
     */
    @Transactional
    public Ticket claimTicket(CurrentUser operator, Long ticketId) {
        Ticket before = support.requireTicket(ticketId);
        requirePendingAssign(before, "只有待分配工单可以认领");
        ticketApprovalService.requireApprovalPassed(before);
        // 认领除了状态校验，还要确认当前用户具备队列或分组权限。
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

    /**
     * 转派工单。
     */
    @Transactional
    public Ticket transferTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        Ticket before = support.requireTicket(ticketId);
        support.permissionService().requireTransfer(operator, before);
        requireProcessing(before, "只有处理中工单可以转派");
        ticketApprovalService.requireApprovalPassed(before);
        support.requireStaffUser(assigneeId);
        return transferAssigneeAndReload(operator, before, assigneeId);
    }

    /**
     * 更新状态。
     */
    @Transactional
    public Ticket updateStatus(CurrentUser operator, Long ticketId, TicketUpdateStatusCommandDTO command) {
        Ticket before = support.requireTicket(ticketId);
        ticketApprovalService.requireApprovalPassed(before);
        TicketStatusEnum targetStatus = command.getTargetStatus();
        if (targetStatus == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATUS_REQUIRED);
        }
        if (targetStatus == TicketStatusEnum.CLOSED) {
            // 关闭动作必须走专门接口，确保知识构建和事件通知不会被绕过。
            throw new BusinessException(BusinessErrorCode.CLOSE_TICKET_USE_CLOSE_API);
        }
        validateStatusTransition(operator, before, targetStatus);
        return updateStatusAndReload(operator, before, targetStatus, resolveSolutionSummary(before, command));
    }

    /**
     * 关闭工单。
     */
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
        // 只有真正关闭后的工单才会进入知识沉淀流程，避免处理中内容过早入库。
        TicketKnowledgeBuildTask task = knowledgeBuildTaskService.createPending(ticketId);
        support.publishTicketClosedAfterCommit(ticketId, task == null ? null : task.getId());
        return after;
    }

    /**
     * 更新处理人并重新加载工单。
     */
    private Ticket updateAssigneeAndReload(
            CurrentUser operator,
            Ticket before,
            Long assigneeId,
            TicketStatusEnum targetStatus,
            OperationTypeEnum operationType,
            String actionName,
            boolean refreshSla
    ) {
        // 更新时带上旧状态做乐观保护，防止并发流转把状态覆盖掉。
        support.requireUpdated(ticketRepository.updateAssigneeAndStatus(
                before.getId(),
                assigneeId,
                before.getStatus(),
                targetStatus
        ));
        Ticket after = support.requireTicket(before.getId());
        if (refreshSla) {
            // 首次进入处理中后刷新 SLA 计时，确保时钟从最新责任人视角重新计算。
            ticketSlaService.createOrRefreshInstance(after);
        }
        return finalizeWorkflowChange(operator, before, after, operationType, actionName);
    }

    /**
     * 转派处理人并重新加载工单。
     */
    private Ticket transferAssigneeAndReload(CurrentUser operator, Ticket before, Long assigneeId) {
        support.requireUpdated(ticketRepository.updateAssignee(before.getId(), assigneeId, before.getStatus()));
        Ticket after = support.requireTicket(before.getId());
        return finalizeWorkflowChange(operator, before, after, OperationTypeEnum.TRANSFER, "转派工单");
    }

    /**
     * 更新状态并重新加载工单。
     */
    private Ticket updateStatusAndReload(
            CurrentUser operator,
            Ticket before,
            TicketStatusEnum targetStatus,
            String solutionSummary
    ) {
        return updateStatusAndReload(operator, before, targetStatus, solutionSummary, OperationTypeEnum.UPDATE_STATUS, "更新工单状态");
    }

    /**
     * 更新状态并重新加载工单。
     */
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

    /**
     * 处理流转变更。
     */
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

    /**
     * 校验操作人是否可以认领工单。
     */
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
        // 队列成员和所属分组负责人都允许认领，其余用户直接拒绝。
        if (!isQueueMember && !isGroupOwner) {
            throw new BusinessException(BusinessErrorCode.TICKET_CLAIM_FORBIDDEN);
        }
    }

    /**
     * 校验工单处于待分配状态。
     */
    private void requirePendingAssign(Ticket ticket, String message) {
        support.requireStatus(ticket, TicketStatusEnum.PENDING_ASSIGN, message);
    }

    /**
     * 校验工单处于处理中状态。
     */
    private void requireProcessing(Ticket ticket, String message) {
        support.requireStatus(ticket, TicketStatusEnum.PROCESSING, message);
    }

    /**
     * 校验工单处于已解决状态。
     */
    private void requireResolved(Ticket ticket, String message) {
        support.requireStatus(ticket, TicketStatusEnum.RESOLVED, message);
    }

    /**
     * 解析解决摘要。
     */
    private String resolveSolutionSummary(Ticket before, TicketUpdateStatusCommandDTO command) {
        return command.getSolutionSummary() == null ? before.getSolutionSummary() : command.getSolutionSummary();
    }

    /**
     * 校验工单状态流转是否合法。
     *
     * <p>使用 TicketStatusTransition 枚举转换表校验，避免手写 if/else 链。</p>
     */
    private void validateStatusTransition(CurrentUser operator, Ticket ticket, TicketStatusEnum targetStatus) {
        TicketStatusEnum current = ticket.getStatus();
        if (current == targetStatus) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATUS_UNCHANGED);
        }

        TicketStatusTransition.TransitionRule rule = TicketStatusTransition.INSTANCE
                .findRule(current, targetStatus)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS_TRANSITION));

        if (rule.isRequiresAdmin()) {
            support.permissionService().requireAdmin(operator);
        }
        if (rule.isRequiresAssignee() && ticket.getAssigneeId() == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_ASSIGNEE_REQUIRED);
        }
        if (rule.isRequiresResolvePermission()) {
            support.permissionService().requireResolve(operator, ticket);
        }
        if (rule.isRequiresClosePermission()) {
            support.permissionService().requireClose(operator, ticket);
        }
    }

    /**
     * 判断用户是否为工单所属分组负责人。
     */
    private boolean isGroupOwner(Ticket ticket, Long userId) {
        if (ticket.getGroupId() == null) {
            return false;
        }
        TicketGroup group = ticketGroupService.get(ticket.getGroupId());
        return userId.equals(group.getOwnerUserId());
    }
}
