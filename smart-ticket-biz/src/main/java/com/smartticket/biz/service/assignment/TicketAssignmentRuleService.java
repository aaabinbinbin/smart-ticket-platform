package com.smartticket.biz.service.assignment;

import com.smartticket.biz.dto.assignment.TicketAssignmentPreviewDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentRuleCommandDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentRulePageQueryDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentStatsDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.assignment.TicketAssignmentRuleRepository;
import com.smartticket.biz.repository.ticket.TicketOperationLogRepository;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.biz.service.ticket.TicketQueryService;
import com.smartticket.biz.service.ticket.TicketQueueBindingService;
import com.smartticket.biz.service.ticket.TicketUserDirectoryService;
import com.smartticket.biz.service.ticket.TicketWorkflowService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.enums.CodeInfoEnum;
import com.smartticket.domain.enums.OperationTypeEnum;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketAssignmentRuleService {
    private final TicketAssignmentRuleRepository ruleRepository;
    private final TicketQueryService ticketQueryService;
    private final TicketWorkflowService ticketWorkflowService;
    private final TicketQueueBindingService ticketQueueBindingService;
    private final TicketGroupService ticketGroupService;
    private final TicketQueueService ticketQueueService;
    private final TicketAssignmentTargetResolver ticketAssignmentTargetResolver;
    private final TicketQueueMemberService ticketQueueMemberService;
    private final TicketOperationLogRepository ticketOperationLogRepository;
    private final TicketPermissionService permissionService;
    private final TicketUserDirectoryService ticketUserDirectoryService;

    public TicketAssignmentRuleService(
            TicketAssignmentRuleRepository ruleRepository,
            TicketQueryService ticketQueryService,
            TicketWorkflowService ticketWorkflowService,
            TicketQueueBindingService ticketQueueBindingService,
            TicketGroupService ticketGroupService,
            TicketQueueService ticketQueueService,
            TicketAssignmentTargetResolver ticketAssignmentTargetResolver,
            TicketQueueMemberService ticketQueueMemberService,
            TicketOperationLogRepository ticketOperationLogRepository,
            TicketPermissionService permissionService,
            TicketUserDirectoryService ticketUserDirectoryService
    ) {
        this.ruleRepository = ruleRepository;
        this.ticketQueryService = ticketQueryService;
        this.ticketWorkflowService = ticketWorkflowService;
        this.ticketQueueBindingService = ticketQueueBindingService;
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueService = ticketQueueService;
        this.ticketAssignmentTargetResolver = ticketAssignmentTargetResolver;
        this.ticketQueueMemberService = ticketQueueMemberService;
        this.ticketOperationLogRepository = ticketOperationLogRepository;
        this.permissionService = permissionService;
        this.ticketUserDirectoryService = ticketUserDirectoryService;
    }

    @Transactional
    public TicketAssignmentRule create(CurrentUser operator, TicketAssignmentRuleCommandDTO command) {
        permissionService.requireAdmin(operator);
        validateRule(command);
        TicketAssignmentRule rule = TicketAssignmentRule.builder()
                .ruleName(command.getRuleName())
                .category(enumCode(command.getCategory()))
                .priority(enumCode(command.getPriority()))
                .targetGroupId(command.getTargetGroupId())
                .targetQueueId(command.getTargetQueueId())
                .targetUserId(command.getTargetUserId())
                .weight(command.getWeight() == null ? 0 : command.getWeight())
                .enabled(toEnabled(command.getEnabled()))
                .build();
        ruleRepository.insert(rule);
        return requireById(rule.getId());
    }

    @Transactional
    public TicketAssignmentRule update(CurrentUser operator, Long ruleId, TicketAssignmentRuleCommandDTO command) {
        permissionService.requireAdmin(operator);
        validateRule(command);
        TicketAssignmentRule rule = requireById(ruleId);
        rule.setRuleName(command.getRuleName());
        rule.setCategory(enumCode(command.getCategory()));
        rule.setPriority(enumCode(command.getPriority()));
        rule.setTargetGroupId(command.getTargetGroupId());
        rule.setTargetQueueId(command.getTargetQueueId());
        rule.setTargetUserId(command.getTargetUserId());
        rule.setWeight(command.getWeight() == null ? 0 : command.getWeight());
        rule.setEnabled(toEnabled(command.getEnabled()));
        ruleRepository.update(rule);
        return requireById(ruleId);
    }

    @Transactional
    public TicketAssignmentRule updateEnabled(CurrentUser operator, Long ruleId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requireById(ruleId);
        ruleRepository.updateEnabled(ruleId, enabled ? 1 : 0);
        return requireById(ruleId);
    }

    public TicketAssignmentRule get(Long ruleId) {
        return requireById(ruleId);
    }

    public PageResult<TicketAssignmentRule> page(TicketAssignmentRulePageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        int offset = (pageNo - 1) * pageSize;
        String category = enumCode(query.getCategory());
        String priority = enumCode(query.getPriority());
        Integer enabled = query.getEnabled() == null ? null : toEnabled(query.getEnabled());
        List<TicketAssignmentRule> records = ruleRepository.page(category, priority, enabled, offset, pageSize);
        long total = ruleRepository.count(category, priority, enabled);
        return PageResult.<TicketAssignmentRule>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    public TicketAssignmentPreviewDTO preview(CurrentUser operator, Long ticketId) {
        Ticket ticket = ticketQueryService.getDetail(operator, ticketId).getTicket();
        TicketAssignmentRule rule = findMatchedRule(ticket);
        if (rule == null) {
            return TicketAssignmentPreviewDTO.builder()
                    .ticketId(ticketId)
                    .matched(false)
                    .reason("No enabled assignment rule matched")
                    .build();
        }
        return TicketAssignmentPreviewDTO.builder()
                .ticketId(ticketId)
                .matched(true)
                .ruleId(rule.getId())
                .ruleName(rule.getRuleName())
                .targetGroupId(rule.getTargetGroupId())
                .targetQueueId(rule.getTargetQueueId())
                .targetUserId(rule.getTargetUserId())
                .reason("Matched assignment rule: " + rule.getRuleName())
                .build();
    }

    public TicketAssignmentStatsDTO stats() {
        long matched = ticketOperationLogRepository.countByOperationType(OperationTypeEnum.AUTO_ASSIGN_MATCHED.getCode());
        long fallback = ticketOperationLogRepository.countByOperationType(OperationTypeEnum.AUTO_ASSIGN_FALLBACK.getCode());
        long pending = ticketOperationLogRepository.countByOperationType(OperationTypeEnum.AUTO_ASSIGN_PENDING.getCode());
        long claimed = ticketOperationLogRepository.countByOperationType(OperationTypeEnum.CLAIM.getCode());
        long total = matched + fallback + pending;
        long assigned = matched + fallback;
        BigDecimal hitRate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(assigned).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        return TicketAssignmentStatsDTO.builder()
                .autoAssignMatchedCount(matched)
                .autoAssignFallbackCount(fallback)
                .autoAssignPendingCount(pending)
                .claimedCount(claimed)
                .totalAutoAssignCount(total)
                .autoAssignedCount(assigned)
                .autoAssignHitRate(hitRate)
                .build();
    }

    @Transactional
    public Ticket autoAssign(CurrentUser operator, Long ticketId) {
        Ticket ticket = ticketQueryService.getDetail(operator, ticketId).getTicket();
        TicketAssignmentRule rule = findMatchedRule(ticket);
        if (rule == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_ASSIGNMENT_RULE_NOT_MATCHED);
        }

        TicketAssignmentTargetResolver.AssignmentTarget target = ticketAssignmentTargetResolver.resolve(rule);
        if (target.groupId() != null && target.queueId() != null) {
            ticketQueueBindingService.bindTicketQueue(operator, ticketId, target.groupId(), target.queueId());
        }
        if (target.assigneeId() == null) {
            writeOperationLog(
                    ticketId,
                    operator.getUserId(),
                    OperationTypeEnum.AUTO_ASSIGN_PENDING,
                    "Auto assignment found no available assignee",
                    null,
                    "groupId=" + target.groupId() + ", queueId=" + target.queueId()
            );
            return ticketQueryService.getDetail(operator, ticketId).getTicket();
        }

        if (target.memberId() != null) {
            ticketQueueMemberService.markAssigned(target.memberId());
            writeOperationLog(
                    ticketId,
                    operator.getUserId(),
                    OperationTypeEnum.AUTO_ASSIGN_MATCHED,
                    "Auto assignment matched queue member",
                    null,
                    "assigneeId=" + target.assigneeId() + ", queueId=" + target.queueId() + ", memberId=" + target.memberId()
            );
        } else if (target.fallback()) {
            writeOperationLog(
                    ticketId,
                    operator.getUserId(),
                    OperationTypeEnum.AUTO_ASSIGN_FALLBACK,
                    "Auto assignment fell back to group owner",
                    null,
                    "assigneeId=" + target.assigneeId() + ", groupId=" + target.groupId()
            );
        } else {
            writeOperationLog(
                    ticketId,
                    operator.getUserId(),
                    OperationTypeEnum.AUTO_ASSIGN_MATCHED,
                    "Auto assignment matched explicit target user",
                    null,
                    "assigneeId=" + target.assigneeId() + ", queueId=" + target.queueId()
            );
        }
        return ticketWorkflowService.assignTicket(operator, ticketId, target.assigneeId());
    }

    private TicketAssignmentRule findMatchedRule(Ticket ticket) {
        return ruleRepository.findBestMatch(enumCode(ticket.getCategory()), enumCode(ticket.getPriority()));
    }

    private TicketAssignmentRule requireById(Long ruleId) {
        TicketAssignmentRule rule = ruleRepository.findById(ruleId);
        if (rule == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_ASSIGNMENT_RULE_NOT_FOUND);
        }
        return rule;
    }

    private void validateRule(TicketAssignmentRuleCommandDTO command) {
        if (command.getTargetGroupId() == null
                && command.getTargetQueueId() == null
                && command.getTargetUserId() == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "Assignment target is required");
        }
        if (command.getTargetGroupId() != null) {
            ticketGroupService.requireEnabled(command.getTargetGroupId());
        }
        TicketQueue queue = null;
        if (command.getTargetQueueId() != null) {
            queue = ticketQueueService.requireEnabled(command.getTargetQueueId());
        }
        if (command.getTargetGroupId() != null && queue != null && !command.getTargetGroupId().equals(queue.getGroupId())) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "Queue does not belong to target group");
        }
        if (command.getTargetUserId() != null) {
            ticketUserDirectoryService.requireStaffUser(command.getTargetUserId());
        }
    }

    private void writeOperationLog(
            Long ticketId,
            Long operatorId,
            OperationTypeEnum operationType,
            String operationDesc,
            String beforeValue,
            String afterValue
    ) {
        ticketOperationLogRepository.insert(TicketOperationLog.builder()
                .ticketId(ticketId)
                .operatorId(operatorId)
                .operationType(operationType)
                .operationDesc(operationDesc)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    private String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }

    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }
}
