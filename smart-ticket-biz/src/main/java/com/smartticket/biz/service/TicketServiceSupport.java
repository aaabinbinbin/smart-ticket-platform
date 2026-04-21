package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.event.TicketClosedEvent;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketCommentRepository;
import com.smartticket.biz.repository.TicketOperationLogRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
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
class TicketServiceSupport {
    private static final DateTimeFormatter TICKET_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketOperationLogRepository operationLogRepository;
    private final TicketPermissionService permissionService;
    private final TicketDetailCacheService ticketDetailCacheService;
    private final TicketIdempotencyService ticketIdempotencyService;
    private final TicketSlaService ticketSlaService;
    private final TicketGroupService ticketGroupService;
    private final TicketQueueService ticketQueueService;
    private final ApplicationEventPublisher eventPublisher;

    TicketServiceSupport(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketOperationLogRepository operationLogRepository,
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
        this.permissionService = permissionService;
        this.ticketDetailCacheService = ticketDetailCacheService;
        this.ticketIdempotencyService = ticketIdempotencyService;
        this.ticketSlaService = ticketSlaService;
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueService = ticketQueueService;
        this.eventPublisher = eventPublisher;
    }

    TicketRepository ticketRepository() {
        return ticketRepository;
    }

    TicketCommentRepository ticketCommentRepository() {
        return ticketCommentRepository;
    }

    TicketPermissionService permissionService() {
        return permissionService;
    }

    TicketDetailCacheService ticketDetailCacheService() {
        return ticketDetailCacheService;
    }

    TicketIdempotencyService ticketIdempotencyService() {
        return ticketIdempotencyService;
    }

    TicketSlaService ticketSlaService() {
        return ticketSlaService;
    }

    TicketGroupService ticketGroupService() {
        return ticketGroupService;
    }

    TicketQueueService ticketQueueService() {
        return ticketQueueService;
    }

    Ticket requireTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    TicketComment requireComment(Long commentId) {
        TicketComment comment = ticketCommentRepository.findById(commentId);
        if (comment == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return comment;
    }

    Ticket requireVisibleTicket(CurrentUser operator, Long ticketId) {
        Ticket ticket = operator.isAdmin()
                ? ticketRepository.findById(ticketId)
                : ticketRepository.findVisibleById(ticketId, operator.getUserId());
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    void requireVisibleFromTicket(CurrentUser operator, Ticket ticket) {
        if (!permissionService.canView(operator, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
    }

    TicketDetailDTO buildDetail(Long ticketId, Ticket ticket) {
        return TicketDetailDTO.builder()
                .ticket(ticket)
                .comments(ticketCommentRepository.findByTicketId(ticketId))
                .operationLogs(operationLogRepository.findByTicketId(ticketId))
                .build();
    }

    void requireUpdated(int affectedRows) {
        if (affectedRows <= 0) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATE_CHANGED);
        }
    }

    void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() > 128) {
            throw new BusinessException(BusinessErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        for (int i = 0; i < idempotencyKey.length(); i++) {
            if (Character.isISOControl(idempotencyKey.charAt(i))) {
                throw new BusinessException(BusinessErrorCode.INVALID_IDEMPOTENCY_KEY);
            }
        }
    }

    void requireStatus(Ticket ticket, TicketStatusEnum expectedStatus, String message) {
        if (ticket.getStatus() != expectedStatus) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, message);
        }
    }

    void requireStaffUser(Long userId) {
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

    void validateQueueBinding(Long groupId, Long queueId) {
        if (groupId == null || queueId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "工单组和队列不能为空");
        }
        ticketGroupService.requireEnabled(groupId);
        TicketQueue queue = ticketQueueService.requireEnabled(queueId);
        if (!groupId.equals(queue.getGroupId())) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "队列不属于指定工单组");
        }
    }

    void writeLog(
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

    String generateTicketNo() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "INC" + LocalDateTime.now().format(TICKET_NO_TIME_FORMATTER) + suffix;
    }

    void saveIdempotencyResultAfterCommit(Long userId, String idempotencyKey, Long ticketId) {
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

    void publishTicketClosedAfterCommit(Long ticketId) {
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

    String snapshot(Ticket ticket) {
        return "id=" + ticket.getId()
                + ", ticketNo=" + ticket.getTicketNo()
                + ", status=" + enumCode(ticket.getStatus())
                + ", assigneeId=" + ticket.getAssigneeId()
                + ", groupId=" + ticket.getGroupId()
                + ", queueId=" + ticket.getQueueId()
                + ", solutionSummary=" + ticket.getSolutionSummary();
    }

    String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }
}
