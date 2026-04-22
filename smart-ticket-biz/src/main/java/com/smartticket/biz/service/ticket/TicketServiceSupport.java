package com.smartticket.biz.service.ticket;

import com.smartticket.biz.dto.ticket.TicketDetailDTO;
import com.smartticket.biz.event.TicketClosedEvent;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.approval.TicketApprovalRepository;
import com.smartticket.biz.repository.ticket.TicketCommentRepository;
import com.smartticket.biz.repository.ticket.TicketOperationLogRepository;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.assignment.TicketGroupService;
import com.smartticket.biz.service.assignment.TicketQueueService;
import com.smartticket.biz.service.sla.TicketSlaService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.enums.CodeInfoEnum;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TicketServiceSupport {
    private static final DateTimeFormatter TICKET_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketOperationLogRepository operationLogRepository;
    private final TicketApprovalRepository ticketApprovalRepository;
    private final TicketPermissionService permissionService;
    private final TicketDetailCacheService ticketDetailCacheService;
    private final TicketIdempotencyService ticketIdempotencyService;
    private final TicketSlaService ticketSlaService;
    private final TicketGroupService ticketGroupService;
    private final TicketQueueService ticketQueueService;
    private final ApplicationEventPublisher eventPublisher;

    public TicketServiceSupport(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketOperationLogRepository operationLogRepository,
            TicketApprovalRepository ticketApprovalRepository,
            TicketPermissionService permissionService,
            TicketDetailCacheService ticketDetailCacheService,
            TicketIdempotencyService ticketIdempotencyService,
            TicketSlaService ticketSlaService,
            TicketGroupService ticketGroupService,
            TicketQueueService ticketQueueService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.operationLogRepository = operationLogRepository;
        this.ticketApprovalRepository = ticketApprovalRepository;
        this.permissionService = permissionService;
        this.ticketDetailCacheService = ticketDetailCacheService;
        this.ticketIdempotencyService = ticketIdempotencyService;
        this.ticketSlaService = ticketSlaService;
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueService = ticketQueueService;
        this.eventPublisher = eventPublisher;
    }

    public TicketRepository ticketRepository() {
        return ticketRepository;
    }

    public TicketCommentRepository ticketCommentRepository() {
        return ticketCommentRepository;
    }

    public TicketPermissionService permissionService() {
        return permissionService;
    }

    public TicketApprovalRepository ticketApprovalRepository() {
        return ticketApprovalRepository;
    }

    public TicketDetailCacheService ticketDetailCacheService() {
        return ticketDetailCacheService;
    }

    public TicketIdempotencyService ticketIdempotencyService() {
        return ticketIdempotencyService;
    }

    public TicketSlaService ticketSlaService() {
        return ticketSlaService;
    }

    public TicketGroupService ticketGroupService() {
        return ticketGroupService;
    }

    public TicketQueueService ticketQueueService() {
        return ticketQueueService;
    }

    public Ticket requireTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    public TicketComment requireComment(Long commentId) {
        TicketComment comment = ticketCommentRepository.findById(commentId);
        if (comment == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return comment;
    }

    public Ticket requireVisibleTicket(CurrentUser operator, Long ticketId) {
        Ticket ticket = operator.isAdmin()
                ? ticketRepository.findById(ticketId)
                : ticketRepository.findVisibleById(ticketId, operator.getUserId());
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    public void requireVisibleFromTicket(CurrentUser operator, Ticket ticket) {
        if (!permissionService.canView(operator, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
    }

    public TicketDetailDTO buildDetail(Long ticketId, Ticket ticket) {
        TicketApproval approval = ticketApprovalRepository.findByTicketId(ticketId);
        return TicketDetailDTO.builder()
                .ticket(ticket)
                .approval(approval)
                .comments(ticketCommentRepository.findByTicketId(ticketId))
                .operationLogs(operationLogRepository.findByTicketId(ticketId))
                .build();
    }

    public void requireUpdated(int affectedRows) {
        if (affectedRows <= 0) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATE_CHANGED);
        }
    }

    public void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() > 128) {
            throw new BusinessException(BusinessErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        for (int i = 0; i < idempotencyKey.length(); i++) {
            if (Character.isISOControl(idempotencyKey.charAt(i))) {
                throw new BusinessException(BusinessErrorCode.INVALID_IDEMPOTENCY_KEY);
            }
        }
    }

    public void requireStatus(Ticket ticket, TicketStatusEnum expectedStatus, String message) {
        if (ticket.getStatus() != expectedStatus) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, message);
        }
    }

    public void requireStaffUser(Long userId) {
        SysUser user = ticketRepository.findUserById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(BusinessErrorCode.ASSIGNEE_NOT_FOUND);
        }
        boolean isStaff = ticketRepository.findRolesByUserId(userId)
                .stream()
                .map(SysRole::getRoleCode)
                .anyMatch("STAFF"::equals);
        if (!isStaff) {
            throw new BusinessException(BusinessErrorCode.ASSIGNEE_NOT_STAFF);
        }
    }

    public void requireApproverUser(Long userId) {
        SysUser user = ticketRepository.findUserById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(BusinessErrorCode.ASSIGNEE_NOT_FOUND);
        }
        boolean allowed = ticketRepository.findRolesByUserId(userId)
                .stream()
                .map(SysRole::getRoleCode)
                .anyMatch(roleCode -> "STAFF".equals(roleCode) || "ADMIN".equals(roleCode));
        if (!allowed) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批人必须具备 STAFF 或 ADMIN 角色");
        }
    }

    public void validateQueueBinding(Long groupId, Long queueId) {
        if (groupId == null || queueId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "工单组和队列不能为空");
        }
        ticketGroupService.requireEnabled(groupId);
        TicketQueue queue = ticketQueueService.requireEnabled(queueId);
        if (!groupId.equals(queue.getGroupId())) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "队列不属于指定工单组");
        }
    }

    public void writeLog(
            Long ticketId,
            Long operatorId,
            OperationTypeEnum operationType,
            String operationDesc,
            String beforeValue,
            String afterValue
    ) {
        TicketOperationLog log = TicketOperationLog.builder()
                .ticketId(ticketId)
                .operatorId(operatorId)
                .operationType(operationType)
                .operationDesc(operationDesc)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build();
        operationLogRepository.insert(log);
    }

    public String generateTicketNo() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "INC" + LocalDateTime.now().format(TICKET_NO_TIME_FORMATTER) + suffix;
    }

    public void saveIdempotencyResultAfterCommit(Long userId, String idempotencyKey, Long ticketId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ticketIdempotencyService.saveCreatedTicketId(userId, idempotencyKey, ticketId);
            ticketIdempotencyService.releaseCreateLock(userId, idempotencyKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ticketIdempotencyService.saveCreatedTicketId(userId, idempotencyKey, ticketId);
            }

            @Override
            public void afterCompletion(int status) {
                ticketIdempotencyService.releaseCreateLock(userId, idempotencyKey);
            }
        });
    }

    public void publishTicketClosedAfterCommit(Long ticketId) {
        TicketClosedEvent event = new TicketClosedEvent(ticketId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publishEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }

    public String snapshot(Ticket ticket) {
        return "id=" + ticket.getId()
                + ", ticketNo=" + ticket.getTicketNo()
                + ", type=" + enumCode(ticket.getType())
                + ", status=" + enumCode(ticket.getStatus())
                + ", assigneeId=" + ticket.getAssigneeId()
                + ", groupId=" + ticket.getGroupId()
                + ", queueId=" + ticket.getQueueId()
                + ", solutionSummary=" + ticket.getSolutionSummary();
    }

    public String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }
}

