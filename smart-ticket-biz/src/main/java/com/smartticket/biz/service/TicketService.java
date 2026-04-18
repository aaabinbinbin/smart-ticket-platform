package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.dto.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketCommentRepository;
import com.smartticket.biz.repository.TicketOperationLogRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.enums.CodeInfoEnum;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 工单核心业务服务。
 *
 * <p>该服务是工单写操作的统一入口，集中处理创建、分配、转派、状态流转、评论、
 * 关闭、权限判断、操作日志、缓存失效和幂等防重。后续 Agent 也应通过本服务操作工单。</p>
 */
@Service
public class TicketService {
    private static final DateTimeFormatter TICKET_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketOperationLogRepository operationLogRepository;
    private final TicketPermissionService permissionService;
    private final TicketDetailCacheService ticketDetailCacheService;
    private final TicketIdempotencyService ticketIdempotencyService;

    public TicketService(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketOperationLogRepository operationLogRepository,
            TicketPermissionService permissionService,
            TicketDetailCacheService ticketDetailCacheService,
            TicketIdempotencyService ticketIdempotencyService
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.operationLogRepository = operationLogRepository;
        this.permissionService = permissionService;
        this.ticketDetailCacheService = ticketDetailCacheService;
        this.ticketIdempotencyService = ticketIdempotencyService;
    }

    @Transactional
    public Ticket createTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        if (ticketIdempotencyService.enabled(command.getIdempotencyKey())) {
            return createTicketWithIdempotency(operator, command);
        }
        return doCreateTicket(operator, command);
    }

    private Ticket createTicketWithIdempotency(CurrentUser operator, TicketCreateCommandDTO command) {
        String idempotencyKey = ticketIdempotencyService.normalize(command.getIdempotencyKey());
        validateIdempotencyKey(idempotencyKey);
        command.setIdempotencyKey(idempotencyKey);
        Long existingTicketId = ticketIdempotencyService.getCreatedTicketId(operator.getUserId(), idempotencyKey);
        if (existingTicketId != null) {
            return requireTicket(existingTicketId);
        }
        if (!ticketIdempotencyService.acquireCreateLock(operator.getUserId(), idempotencyKey)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENT_REQUEST_PROCESSING);
        }
        try {
            Ticket ticket = doCreateTicket(operator, command);
            saveIdempotencyResultAfterCommit(operator.getUserId(), idempotencyKey, ticket.getId());
            return ticket;
        } catch (RuntimeException ex) {
            ticketIdempotencyService.releaseCreateLock(operator.getUserId(), idempotencyKey);
            throw ex;
        }
    }

    private Ticket doCreateTicket(CurrentUser operator, TicketCreateCommandDTO command) {
        Ticket ticket = Ticket.builder()
                .ticketNo(generateTicketNo())
                .title(command.getTitle())
                .description(command.getDescription())
                .category(command.getCategory())
                .priority(command.getPriority())
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(operator.getUserId())
                .source("MANUAL")
                .idempotencyKey(command.getIdempotencyKey())
                .build();
        ticketRepository.insert(ticket);
        writeLog(ticket.getId(), operator.getUserId(), OperationTypeEnum.CREATE, "创建工单", null, snapshot(ticket));
        return requireTicket(ticket.getId());
    }

    public TicketDetailDTO getDetail(CurrentUser operator, Long ticketId) {
        TicketDetailDTO cached = ticketDetailCacheService.get(ticketId);
        if (cached != null && cached.getTicket() != null) {
            requireVisibleFromTicket(operator, cached.getTicket());
            return cached;
        }
        Ticket ticket = requireVisibleTicket(operator, ticketId);
        TicketDetailDTO detail = TicketDetailDTO.builder()
                .ticket(ticket)
                .comments(ticketCommentRepository.findByTicketId(ticketId))
                .operationLogs(operationLogRepository.findByTicketId(ticketId))
                .build();
        ticketDetailCacheService.put(ticketId, detail);
        return detail;
    }

    private void saveIdempotencyResultAfterCommit(Long userId, String idempotencyKey, Long ticketId) {
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

    public PageResult<Ticket> pageTickets(CurrentUser operator, TicketPageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        int offset = (pageNo - 1) * pageSize;
        String status = enumCode(query.getStatus());
        String category = enumCode(query.getCategory());
        String priority = enumCode(query.getPriority());

        List<Ticket> records;
        long total;
        if (operator.isAdmin()) {
            records = ticketRepository.pageAll(status, category, priority, offset, pageSize);
            total = ticketRepository.countAll(status, category, priority);
        } else {
            records = ticketRepository.pageVisible(operator.getUserId(), status, category, priority, offset, pageSize);
            total = ticketRepository.countVisible(operator.getUserId(), status, category, priority);
        }

        return PageResult.<Ticket>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    @Transactional
    public Ticket assignTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        permissionService.requireAdmin(operator);
        Ticket before = requireTicket(ticketId);
        requireStatus(before, TicketStatusEnum.PENDING_ASSIGN, "只有待分配工单可以执行分配");
        requireStaffUser(assigneeId);

        requireUpdated(ticketRepository.updateAssigneeAndStatus(
                ticketId,
                assigneeId,
                before.getStatus(),
                TicketStatusEnum.PROCESSING
        ));
        Ticket after = requireTicket(ticketId);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.ASSIGN, "分配工单", snapshot(before), snapshot(after));
        ticketDetailCacheService.evict(ticketId);
        return after;
    }

    @Transactional
    public Ticket transferTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        Ticket before = requireTicket(ticketId);
        permissionService.requireTransfer(operator, before);
        requireStatus(before, TicketStatusEnum.PROCESSING, "只有处理中的工单可以转派");
        requireStaffUser(assigneeId);

        requireUpdated(ticketRepository.updateAssignee(ticketId, assigneeId, before.getStatus()));
        Ticket after = requireTicket(ticketId);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.TRANSFER, "转派工单", snapshot(before), snapshot(after));
        ticketDetailCacheService.evict(ticketId);
        return after;
    }

    @Transactional
    public Ticket updateStatus(CurrentUser operator, Long ticketId, TicketUpdateStatusCommandDTO command) {
        Ticket before = requireTicket(ticketId);
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
        requireUpdated(ticketRepository.updateStatus(ticketId, before.getStatus(), targetStatus, solutionSummary));
        Ticket after = requireTicket(ticketId);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.UPDATE_STATUS, "更新工单状态", snapshot(before), snapshot(after));
        ticketDetailCacheService.evict(ticketId);
        return after;
    }

    @Transactional
    public TicketComment addComment(CurrentUser operator, Long ticketId, String content) {
        Ticket ticket = requireVisibleTicket(operator, ticketId);
        if (ticket.getStatus() == TicketStatusEnum.CLOSED) {
            throw new BusinessException(BusinessErrorCode.TICKET_CLOSED);
        }

        TicketComment comment = TicketComment.builder()
                .ticketId(ticketId)
                .commenterId(operator.getUserId())
                .commentType("USER_REPLY")
                .content(content)
                .build();
        ticketCommentRepository.insert(comment);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.COMMENT, "添加工单评论", null, "content=" + content);
        ticketDetailCacheService.evict(ticketId);
        return requireComment(comment.getId());
    }

    @Transactional
    public Ticket closeTicket(CurrentUser operator, Long ticketId) {
        Ticket before = requireTicket(ticketId);
        permissionService.requireClose(operator, before);
        requireStatus(before, TicketStatusEnum.RESOLVED, "只有已解决工单可以关闭");

        requireUpdated(ticketRepository.updateStatus(
                ticketId,
                before.getStatus(),
                TicketStatusEnum.CLOSED,
                before.getSolutionSummary()
        ));
        Ticket after = requireTicket(ticketId);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.CLOSE, "关闭工单", snapshot(before), snapshot(after));
        ticketDetailCacheService.evict(ticketId);
        return after;
    }

    private void validateStatusTransition(CurrentUser operator, Ticket ticket, TicketStatusEnum targetStatus) {
        TicketStatusEnum current = ticket.getStatus();
        if (current == targetStatus) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATUS_UNCHANGED);
        }

        if (current == TicketStatusEnum.PENDING_ASSIGN && targetStatus == TicketStatusEnum.PROCESSING) {
            permissionService.requireAdmin(operator);
            if (ticket.getAssigneeId() == null) {
                throw new BusinessException(BusinessErrorCode.TICKET_ASSIGNEE_REQUIRED);
            }
            return;
        }

        if (current == TicketStatusEnum.PROCESSING && targetStatus == TicketStatusEnum.RESOLVED) {
            permissionService.requireResolve(operator, ticket);
            return;
        }

        if (current == TicketStatusEnum.RESOLVED && targetStatus == TicketStatusEnum.CLOSED) {
            permissionService.requireClose(operator, ticket);
            return;
        }

        throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS_TRANSITION);
    }

    private Ticket requireTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    private TicketComment requireComment(Long commentId) {
        TicketComment comment = ticketCommentRepository.findById(commentId);
        if (comment == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return comment;
    }

    private Ticket requireVisibleTicket(CurrentUser operator, Long ticketId) {
        Ticket ticket = operator.isAdmin()
                ? ticketRepository.findById(ticketId)
                : ticketRepository.findVisibleById(ticketId, operator.getUserId());
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
    }

    private void requireVisibleFromTicket(CurrentUser operator, Ticket ticket) {
        if (!permissionService.canView(operator, ticket)) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
    }

    private void requireUpdated(int affectedRows) {
        if (affectedRows <= 0) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATE_CHANGED);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.length() > 128) {
            throw new BusinessException(BusinessErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        for (int i = 0; i < idempotencyKey.length(); i++) {
            if (Character.isISOControl(idempotencyKey.charAt(i))) {
                throw new BusinessException(BusinessErrorCode.INVALID_IDEMPOTENCY_KEY);
            }
        }
    }

    private void requireStatus(Ticket ticket, TicketStatusEnum expectedStatus, String message) {
        if (ticket.getStatus() != expectedStatus) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, message);
        }
    }

    private void requireStaffUser(Long userId) {
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

    private void writeLog(
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

    private String generateTicketNo() {
        int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "INC" + LocalDateTime.now().format(TICKET_NO_TIME_FORMATTER) + suffix;
    }

    private String snapshot(Ticket ticket) {
        return "id=" + ticket.getId()
                + ", ticketNo=" + ticket.getTicketNo()
                + ", status=" + enumCode(ticket.getStatus())
                + ", assigneeId=" + ticket.getAssigneeId()
                + ", solutionSummary=" + ticket.getSolutionSummary();
    }

    private String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }
}
