package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketAssignmentPreviewDTO;
import com.smartticket.biz.dto.TicketAssignmentRuleCommandDTO;
import com.smartticket.biz.dto.TicketAssignmentRulePageQueryDTO;
import com.smartticket.biz.dto.TicketAssignmentStatsDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketAssignmentRuleRepository;
import com.smartticket.biz.repository.TicketOperationLogRepository;
import com.smartticket.biz.repository.TicketQueueRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.entity.TicketQueueMember;
import com.smartticket.domain.enums.CodeInfoEnum;
import com.smartticket.domain.enums.OperationTypeEnum;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final TicketQueueRepository ticketQueueRepository;
    private final TicketQueueMemberService ticketQueueMemberService;
    private final TicketRepository ticketRepository;
    private final TicketOperationLogRepository ticketOperationLogRepository;
    private final TicketPermissionService permissionService;

    public TicketAssignmentRuleService(
            TicketAssignmentRuleRepository ruleRepository,
            TicketQueryService ticketQueryService,
            TicketWorkflowService ticketWorkflowService,
            TicketQueueBindingService ticketQueueBindingService,
            TicketGroupService ticketGroupService,
            TicketQueueService ticketQueueService,
            TicketQueueRepository ticketQueueRepository,
            TicketQueueMemberService ticketQueueMemberService,
            TicketRepository ticketRepository,
            TicketOperationLogRepository ticketOperationLogRepository,
            TicketPermissionService permissionService
    ) {
        this.ruleRepository = ruleRepository;
        this.ticketQueryService = ticketQueryService;
        this.ticketWorkflowService = ticketWorkflowService;
        this.ticketQueueBindingService = ticketQueueBindingService;
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueService = ticketQueueService;
        this.ticketQueueRepository = ticketQueueRepository;
        this.ticketQueueMemberService = ticketQueueMemberService;
        this.ticketRepository = ticketRepository;
        this.ticketOperationLogRepository = ticketOperationLogRepository;
        this.permissionService = permissionService;
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
        TicketAssignmentRule rule = ruleRepository.findBestMatch(enumCode(ticket.getCategory()), enumCode(ticket.getPriority()));
        if (rule == null) {
            return TicketAssignmentPreviewDTO.builder()
                    .ticketId(ticketId)
                    .matched(false)
                    .reason("未匹配到启用的自动分派规则")
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
                .reason("命中自动分派规则: " + rule.getRuleName())
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
                : BigDecimal.valueOf(assigned)
                        .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
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
        TicketAssignmentRule rule = ruleRepository.findBestMatch(enumCode(ticket.getCategory()), enumCode(ticket.getPriority()));
        if (rule == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_ASSIGNMENT_RULE_NOT_MATCHED);
        }

        AssignmentTarget target = resolveAssignmentTarget(rule);
        if (target.groupId() != null && target.queueId() != null) {
            ticketQueueBindingService.bindTicketQueue(operator, ticketId, target.groupId(), target.queueId());
        }
        if (target.assigneeId() == null) {
            writeOperationLog(ticketId, operator.getUserId(), OperationTypeEnum.AUTO_ASSIGN_PENDING,
                    "自动分派未找到可用处理人，转为待认领", null, "groupId=" + target.groupId() + ", queueId=" + target.queueId());
            return ticketQueryService.getDetail(operator, ticketId).getTicket();
        }
        if (target.memberId() != null) {
            ticketQueueMemberService.markAssigned(target.memberId());
            writeOperationLog(ticketId, operator.getUserId(), OperationTypeEnum.AUTO_ASSIGN_MATCHED,
                    "自动分派命中队列成员", null,
                    "assigneeId=" + target.assigneeId() + ", queueId=" + target.queueId() + ", memberId=" + target.memberId());
        } else if (target.fallback()) {
            writeOperationLog(ticketId, operator.getUserId(), OperationTypeEnum.AUTO_ASSIGN_FALLBACK,
                    "自动分派回退到组负责人", null,
                    "assigneeId=" + target.assigneeId() + ", groupId=" + target.groupId());
        } else {
            writeOperationLog(ticketId, operator.getUserId(), OperationTypeEnum.AUTO_ASSIGN_MATCHED,
                    "自动分派命中指定处理人", null,
                    "assigneeId=" + target.assigneeId() + ", queueId=" + target.queueId());
        }
        return ticketWorkflowService.assignTicket(operator, ticketId, target.assigneeId());
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
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "至少需要一个分派目标");
        }
        if (command.getTargetGroupId() != null) {
            ticketGroupService.requireEnabled(command.getTargetGroupId());
        }
        TicketQueue queue = null;
        if (command.getTargetQueueId() != null) {
            queue = ticketQueueService.requireEnabled(command.getTargetQueueId());
        }
        if (command.getTargetGroupId() != null && queue != null && !command.getTargetGroupId().equals(queue.getGroupId())) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "目标队列不属于目标工单组");
        }
        if (command.getTargetUserId() != null) {
            requireStaffUser(command.getTargetUserId());
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

    private AssignmentTarget resolveAssignmentTarget(TicketAssignmentRule rule) {
        if (rule.getTargetUserId() != null) {
            return new AssignmentTarget(rule.getTargetGroupId(), rule.getTargetQueueId(), rule.getTargetUserId(), null, false);
        }

        Long groupId = rule.getTargetGroupId();
        Long queueId = rule.getTargetQueueId();
        if (queueId != null && groupId == null) {
            groupId = ticketQueueService.get(queueId).getGroupId();
        }

        List<TicketQueue> candidateQueues = resolveCandidateQueues(groupId, queueId);
        QueueCandidate selectedCandidate = selectLeastLoadedCandidate(candidateQueues);
        if (selectedCandidate != null) {
            return new AssignmentTarget(
                    selectedCandidate.groupId(),
                    selectedCandidate.queueId(),
                    selectedCandidate.assigneeId(),
                    selectedCandidate.memberId(),
                    false
            );
        }

        Long fallbackAssigneeId = resolveGroupOwnerFallback(groupId);
        Long fallbackQueueId = queueId != null ? queueId : candidateQueues.stream()
                .map(TicketQueue::getId)
                .findFirst()
                .orElse(null);
        return new AssignmentTarget(groupId, fallbackQueueId, fallbackAssigneeId, null, fallbackAssigneeId != null);
    }

    private List<TicketQueue> resolveCandidateQueues(Long groupId, Long queueId) {
        if (queueId != null) {
            return List.of(ticketQueueService.requireEnabled(queueId));
        }
        if (groupId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "真实分派至少需要配置组、队列或处理人");
        }
        ticketGroupService.requireEnabled(groupId);
        return ticketQueueRepository.findEnabledByGroupId(groupId);
    }

    private QueueCandidate selectLeastLoadedCandidate(List<TicketQueue> candidateQueues) {
        List<QueueCandidate> candidates = new ArrayList<>();
        for (TicketQueue queue : candidateQueues) {
            for (TicketQueueMember member : ticketQueueMemberService.listEnabledMembers(queue.getId())) {
                candidates.add(new QueueCandidate(
                        queue.getGroupId(),
                        queue.getId(),
                        member.getId(),
                        member.getUserId(),
                        ticketRepository.countOpenAssignedTickets(member.getUserId()),
                        member.getLastAssignedAt()
                ));
            }
        }
        return candidates.stream()
                .min(Comparator.comparingLong(QueueCandidate::load)
                        .thenComparing(QueueCandidate::lastAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(QueueCandidate::memberId))
                .orElse(null);
    }

    private Long resolveGroupOwnerFallback(Long groupId) {
        if (groupId == null) {
            return null;
        }
        TicketGroup group = ticketGroupService.requireEnabled(groupId);
        if (group.getOwnerUserId() == null) {
            return null;
        }
        SysUser user = ticketRepository.findUserById(group.getOwnerUserId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return null;
        }
        boolean isStaff = ticketRepository.findRolesByUserId(group.getOwnerUserId())
                .stream()
                .map(SysRole::getRoleCode)
                .anyMatch("STAFF"::equals);
        return isStaff ? group.getOwnerUserId() : null;
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

    private record AssignmentTarget(Long groupId, Long queueId, Long assigneeId, Long memberId, boolean fallback) {
    }

    private record QueueCandidate(
            Long groupId,
            Long queueId,
            Long memberId,
            Long assigneeId,
            long load,
            LocalDateTime lastAssignedAt
    ) {
    }
}
