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
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 收敛 ticket 子域里仍然通用的基础支持逻辑。
 * 这里只保留跨多个服务都会用到的校验、详情装配、日志记录和事务后置动作。
 */
@Component
public class TicketServiceSupport {
    private static final DateTimeFormatter TICKET_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketOperationLogRepository operationLogRepository;
    private final TicketApprovalRepository ticketApprovalRepository;
    private final TicketPermissionService permissionService;
    private final TicketUserDirectoryService ticketUserDirectoryService;
    private final TicketIdempotencyService ticketIdempotencyService;
    private final TicketGroupService ticketGroupService;
    private final TicketQueueService ticketQueueService;
    private final ApplicationEventPublisher eventPublisher;

    public TicketServiceSupport(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketOperationLogRepository operationLogRepository,
            TicketApprovalRepository ticketApprovalRepository,
            TicketPermissionService permissionService,
            TicketUserDirectoryService ticketUserDirectoryService,
            TicketIdempotencyService ticketIdempotencyService,
            TicketGroupService ticketGroupService,
            TicketQueueService ticketQueueService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.operationLogRepository = operationLogRepository;
        this.ticketApprovalRepository = ticketApprovalRepository;
        this.permissionService = permissionService;
        this.ticketUserDirectoryService = ticketUserDirectoryService;
        this.ticketIdempotencyService = ticketIdempotencyService;
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueService = ticketQueueService;
        this.eventPublisher = eventPublisher;
    }

    public TicketPermissionService permissionService() {
        return permissionService;
    }

    public TicketApprovalRepository ticketApprovalRepository() {
        return ticketApprovalRepository;
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
        return TicketDetailDTO.builder()
                .ticket(ticket)
                .approval(loadApproval(ticketId))
                .comments(loadComments(ticketId))
                .operationLogs(loadOperationLogs(ticketId))
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
        ticketUserDirectoryService.requireStaffUser(userId);
    }

    public void requireApproverUser(Long userId) {
        ticketUserDirectoryService.requireApproverUser(userId);
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
            saveIdempotencyResult(userId, idempotencyKey, ticketId);
            releaseIdempotencyLock(userId, idempotencyKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                saveIdempotencyResult(userId, idempotencyKey, ticketId);
            }

            @Override
            public void afterCompletion(int status) {
                releaseIdempotencyLock(userId, idempotencyKey);
            }
        });
    }

    public void publishTicketClosedAfterCommit(Long ticketId) {
        TicketClosedEvent event = new TicketClosedEvent(ticketId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishClosedEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishClosedEvent(event);
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

    private TicketApproval loadApproval(Long ticketId) {
        return ticketApprovalRepository.findByTicketId(ticketId);
    }

    private List<TicketComment> loadComments(Long ticketId) {
        return ticketCommentRepository.findByTicketId(ticketId);
    }

    private List<TicketOperationLog> loadOperationLogs(Long ticketId) {
        return operationLogRepository.findByTicketId(ticketId);
    }

    private void saveIdempotencyResult(Long userId, String idempotencyKey, Long ticketId) {
        ticketIdempotencyService.saveCreatedTicketId(userId, idempotencyKey, ticketId);
    }

    private void releaseIdempotencyLock(Long userId, String idempotencyKey) {
        ticketIdempotencyService.releaseCreateLock(userId, idempotencyKey);
    }

    private void publishClosedEvent(TicketClosedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
