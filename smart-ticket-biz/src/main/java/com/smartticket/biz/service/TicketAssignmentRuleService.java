package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketAssignmentPreviewDTO;
import com.smartticket.biz.dto.TicketAssignmentRuleCommandDTO;
import com.smartticket.biz.dto.TicketAssignmentRulePageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketAssignmentRuleRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.enums.CodeInfoEnum;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自动分派规则业务服务。
 *
 * <p>当前 P1 只实现 preview，不执行真实分派。真实分派后续必须复用工单主流程的分配服务。</p>
 */
@Service
public class TicketAssignmentRuleService {
    /** 自动分派规则仓储。 */
    private final TicketAssignmentRuleRepository ruleRepository;

    /** 工单主服务，用于 preview 前复用详情权限判断。 */
    private final TicketService ticketService;

    /** 工单组服务，用于校验目标组。 */
    private final TicketGroupService ticketGroupService;

    /** 工单队列服务，用于校验目标队列。 */
    private final TicketQueueService ticketQueueService;

    /** 工单仓储，用于校验目标处理人是否为 STAFF。 */
    private final TicketRepository ticketRepository;

    /** 权限服务，用于复用 ADMIN 判断。 */
    private final TicketPermissionService permissionService;

    public TicketAssignmentRuleService(
            TicketAssignmentRuleRepository ruleRepository,
            TicketService ticketService,
            TicketGroupService ticketGroupService,
            TicketQueueService ticketQueueService,
            TicketRepository ticketRepository,
            TicketPermissionService permissionService
    ) {
        this.ruleRepository = ruleRepository;
        this.ticketService = ticketService;
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueService = ticketQueueService;
        this.ticketRepository = ticketRepository;
        this.permissionService = permissionService;
    }

    /** 创建自动分派规则。 */
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

    /** 更新自动分派规则。 */
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

    /** 启用或停用自动分派规则。 */
    @Transactional
    public TicketAssignmentRule updateEnabled(CurrentUser operator, Long ruleId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requireById(ruleId);
        ruleRepository.updateEnabled(ruleId, enabled ? 1 : 0);
        return requireById(ruleId);
    }

    /** 查询规则详情。 */
    public TicketAssignmentRule get(Long ruleId) {
        return requireById(ruleId);
    }

    /** 分页查询规则。 */
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

    /**
     * 预览某张工单的自动分派推荐结果。
     *
     * <p>该方法不更新工单，不写操作日志，只返回推荐目标和命中原因。</p>
     */
    public TicketAssignmentPreviewDTO preview(CurrentUser operator, Long ticketId) {
        Ticket ticket = ticketService.getDetail(operator, ticketId).getTicket();
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

    /**
     * 按自动分派规则执行真实分派。
     *
     * <p>该方法只在命中规则且规则明确配置 {@code targetUserId} 时执行。
     * 真实写操作委托给 {@link TicketService#assignTicket(CurrentUser, Long, Long)}，
     * 复用现有 ADMIN 权限、工单状态机、目标处理人校验、操作日志和 SLA 刷新。</p>
     */
    public Ticket autoAssign(CurrentUser operator, Long ticketId) {
        Ticket ticket = ticketService.getDetail(operator, ticketId).getTicket();
        TicketAssignmentRule rule = ruleRepository.findBestMatch(enumCode(ticket.getCategory()), enumCode(ticket.getPriority()));
        if (rule == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_ASSIGNMENT_RULE_NOT_MATCHED);
        }
        if (rule.getTargetUserId() == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "真实分派需要规则配置目标处理人");
        }
        if (rule.getTargetQueueId() != null) {
            Long groupId = rule.getTargetGroupId();
            if (groupId == null) {
                groupId = ticketQueueService.get(rule.getTargetQueueId()).getGroupId();
            }
            ticketService.bindTicketQueue(operator, ticketId, groupId, rule.getTargetQueueId());
        }
        return ticketService.assignTicket(operator, ticketId, rule.getTargetUserId());
    }

    /** 根据 ID 查询规则，不存在时抛出业务异常。 */
    private TicketAssignmentRule requireById(Long ruleId) {
        TicketAssignmentRule rule = ruleRepository.findById(ruleId);
        if (rule == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_ASSIGNMENT_RULE_NOT_FOUND);
        }
        return rule;
    }

    /** 校验自动分派规则目标。 */
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

    /** 校验目标处理人存在且具备 STAFF 角色。 */
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

    /** 将枚举转换成 code。 */
    private String enumCode(CodeInfoEnum value) {
        return value == null ? null : value.getCode();
    }

    /** 将布尔值转换为数据库启停标记。 */
    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }
}
