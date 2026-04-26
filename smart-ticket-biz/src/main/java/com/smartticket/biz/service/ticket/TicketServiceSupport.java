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
import com.smartticket.domain.enums.TicketTypeEnum;
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

    // 工单仓储
    private final TicketRepository ticketRepository;
    // 工单评论仓储
    private final TicketCommentRepository ticketCommentRepository;
    // 操作日志仓储
    private final TicketOperationLogRepository operationLogRepository;
    // 工单审批仓储
    private final TicketApprovalRepository ticketApprovalRepository;
    // 权限服务
    private final TicketPermissionService permissionService;
    // 工单用户目录服务
    private final TicketUserDirectoryService ticketUserDirectoryService;
    // 工单幂等服务
    private final TicketIdempotencyService ticketIdempotencyService;
    // 工单分组服务
    private final TicketGroupService ticketGroupService;
    // 工单队列服务
    private final TicketQueueService ticketQueueService;
    // 事件发布器
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 构造工单服务支撑。
     */
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

    /**
     * 获取权限服务。
     */
    public TicketPermissionService permissionService() {
        return permissionService;
    }

    /**
     * 获取审批仓储。
     */
    public TicketApprovalRepository ticketApprovalRepository() {
        return ticketApprovalRepository;
    }

    /**
     * 获取队列服务。
     */
    public TicketQueueService ticketQueueService() {
        return ticketQueueService;
    }

    /**
     * 校验工单。
     */
    public Ticket requireTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    /**
     * 校验评论。
     */
    public TicketComment requireComment(Long commentId) {
        TicketComment comment = ticketCommentRepository.findById(commentId);
        if (comment == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return comment;
    }

    /**
     * 校验可见工单。
     */
    public Ticket requireVisibleTicket(CurrentUser operator, Long ticketId) {
        Ticket ticket = operator.isAdmin()
                ? ticketRepository.findById(ticketId)
                : ticketRepository.findVisibleById(ticketId, operator.getUserId());
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    /**
     * 校验当前用户是否可查看指定工单。
     */
    public void requireVisibleFromTicket(CurrentUser operator, Ticket ticket) {
        if (!permissionService.canView(operator, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
    }

    /**
     * 构建详情。
     */
    public TicketDetailDTO buildDetail(Long ticketId, Ticket ticket) {
        return TicketDetailDTO.builder()
                .ticket(ticket)
                .approval(loadApproval(ticketId))
                .comments(loadComments(ticketId))
                .operationLogs(loadOperationLogs(ticketId))
                .build();
    }

    /**
     * 校验更新。
     */
    public void requireUpdated(int affectedRows) {
        if (affectedRows <= 0) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATE_CHANGED);
        }
    }

    /**
     * 校验幂等键。
     */
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

    /**
     * 校验状态。
     */
    public void requireStatus(Ticket ticket, TicketStatusEnum expectedStatus, String message) {
        if (ticket.getStatus() != expectedStatus) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, message);
        }
    }

    /**
     * 校验指定用户必须为处理人员。
     */
    public void requireStaffUser(Long userId) {
        ticketUserDirectoryService.requireStaffUser(userId);
    }

    /**
     * 校验工单分组与队列的绑定关系是否合法。
     */
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

    /**
     * 写入工单操作日志。
     */
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

    /**
     * 生成工单编号，前缀根据工单类型派生。
     */
    public String generateTicketNo(TicketTypeEnum type) {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        String prefix = switch (type == null ? TicketTypeEnum.INCIDENT : type) {
            case INCIDENT -> "INC";
            case ACCESS_REQUEST -> "ACC";
            case ENVIRONMENT_REQUEST -> "ENV";
            case CONSULTATION -> "CNS";
            case CHANGE_REQUEST -> "CHG";
        };
        return prefix + LocalDateTime.now().format(TICKET_NO_TIME_FORMATTER) + suffix;
    }

    /**
     * 在事务提交后保存幂等结果。
     */
    public void saveIdempotencyResultAfterCommit(Long userId, String idempotencyKey, Long ticketId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            saveIdempotencyResult(userId, idempotencyKey, ticketId);
            releaseIdempotencyLock(userId, idempotencyKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务提交后写入幂等结果。
             */
            @Override
            public void afterCommit() {
                saveIdempotencyResult(userId, idempotencyKey, ticketId);
            }

            /**
             * 事务结束后释放幂等锁。
             */
            @Override
            public void afterCompletion(int status) {
                releaseIdempotencyLock(userId, idempotencyKey);
            }
        });
    }

    /**
     * 在事务提交后处理工单关闭事件。
     */
    public void publishTicketClosedAfterCommit(Long ticketId, Long knowledgeBuildTaskId) {
        TicketClosedEvent event = new TicketClosedEvent(ticketId, knowledgeBuildTaskId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishClosedEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务提交后发布工单关闭事件。
             */
            @Override
            public void afterCommit() {
                publishClosedEvent(event);
            }
        });
    }

    /**
     * 生成快照。
     */
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

    /**
     * 读取枚举编码值。
     */
    public String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }

    /**
     * 加载审批。
     */
    private TicketApproval loadApproval(Long ticketId) {
        return ticketApprovalRepository.findByTicketId(ticketId);
    }

    /**
     * 加载Comments。
     */
    private List<TicketComment> loadComments(Long ticketId) {
        return ticketCommentRepository.findByTicketId(ticketId);
    }

    /**
     * 加载操作Logs。
     */
    private List<TicketOperationLog> loadOperationLogs(Long ticketId) {
        return operationLogRepository.findByTicketId(ticketId);
    }

    /**
     * 保存幂等结果。
     */
    private void saveIdempotencyResult(Long userId, String idempotencyKey, Long ticketId) {
        ticketIdempotencyService.saveCreatedTicketId(userId, idempotencyKey, ticketId);
    }

    /**
     * 释放幂等Lock。
     */
    private void releaseIdempotencyLock(Long userId, String idempotencyKey) {
        ticketIdempotencyService.releaseCreateLock(userId, idempotencyKey);
    }

    /**
     * 处理工单关闭事件。
     */
    private void publishClosedEvent(TicketClosedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
