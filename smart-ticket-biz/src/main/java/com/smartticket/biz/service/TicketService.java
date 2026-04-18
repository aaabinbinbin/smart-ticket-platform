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

/**
 * Core ticket business service.
 */
@Service
public class TicketService {
    private static final DateTimeFormatter TICKET_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketOperationLogRepository operationLogRepository;
    private final TicketPermissionService permissionService;

    public TicketService(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketOperationLogRepository operationLogRepository,
            TicketPermissionService permissionService
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.operationLogRepository = operationLogRepository;
        this.permissionService = permissionService;
    }

    @Transactional
    public Ticket createTicket(CurrentUser operator, TicketCreateCommandDTO command) {
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
        Ticket ticket = requireVisibleTicket(operator, ticketId);
        return TicketDetailDTO.builder()
                .ticket(ticket)
                .comments(ticketCommentRepository.findByTicketId(ticketId))
                .operationLogs(operationLogRepository.findByTicketId(ticketId))
                .build();
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

        ticketRepository.updateAssigneeAndStatus(ticketId, assigneeId, TicketStatusEnum.PROCESSING);
        Ticket after = requireTicket(ticketId);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.ASSIGN, "分配工单", snapshot(before), snapshot(after));
        return after;
    }

    @Transactional
    public Ticket transferTicket(CurrentUser operator, Long ticketId, Long assigneeId) {
        Ticket before = requireTicket(ticketId);
        permissionService.requireTransfer(operator, before);
        requireStatus(before, TicketStatusEnum.PROCESSING, "只有处理中的工单可以转派");
        requireStaffUser(assigneeId);

        ticketRepository.updateAssignee(ticketId, assigneeId);
        Ticket after = requireTicket(ticketId);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.TRANSFER, "转派工单", snapshot(before), snapshot(after));
        return after;
    }

    @Transactional
    public Ticket updateStatus(CurrentUser operator, Long ticketId, TicketUpdateStatusCommandDTO command) {
        Ticket before = requireTicket(ticketId);
        TicketStatusEnum targetStatus = command.getTargetStatus();
        if (targetStatus == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_STATUS_REQUIRED);
        }
        validateStatusTransition(operator, before, targetStatus);

        String solutionSummary = command.getSolutionSummary() == null
                ? before.getSolutionSummary()
                : command.getSolutionSummary();
        ticketRepository.updateStatus(ticketId, targetStatus, solutionSummary);
        Ticket after = requireTicket(ticketId);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.UPDATE_STATUS, "更新工单状态", snapshot(before), snapshot(after));
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
        return comment;
    }

    @Transactional
    public Ticket closeTicket(CurrentUser operator, Long ticketId) {
        Ticket before = requireTicket(ticketId);
        permissionService.requireClose(operator, before);
        requireStatus(before, TicketStatusEnum.RESOLVED, "只有已解决工单可以关闭");

        ticketRepository.updateStatus(ticketId, TicketStatusEnum.CLOSED, before.getSolutionSummary());
        Ticket after = requireTicket(ticketId);
        writeLog(ticketId, operator.getUserId(), OperationTypeEnum.CLOSE, "关闭工单", snapshot(before), snapshot(after));
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

    private Ticket requireVisibleTicket(CurrentUser operator, Long ticketId) {
        Ticket ticket = operator.isAdmin()
                ? ticketRepository.findById(ticketId)
                : ticketRepository.findVisibleById(ticketId, operator.getUserId());
        if (ticket == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND);
        }
        return ticket;
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
