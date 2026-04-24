package com.smartticket.biz.service.assignment;

import com.smartticket.biz.dto.assignment.TicketAssignmentPreviewDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentRuleCommandDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentRulePageQueryDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentStatsDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketAssignmentRule;
import org.springframework.stereotype.Service;

/**
 * 分派子域门面。
 *
 * <p>对上层隐藏规则管理、目标解析和自动分派执行的内部细节，
 * 调用方只需要面向“分派用例”本身。</p>
 */
@Service
public class TicketAssignmentService {
    // 工单Assignment规则服务
    private final TicketAssignmentRuleService ticketAssignmentRuleService;

    /**
     * 构造工单分派服务。
     */
    public TicketAssignmentService(TicketAssignmentRuleService ticketAssignmentRuleService) {
        this.ticketAssignmentRuleService = ticketAssignmentRuleService;
    }

    /**
     * 创建规则。
     */
    public TicketAssignmentRule createRule(CurrentUser operator, TicketAssignmentRuleCommandDTO command) {
        return ticketAssignmentRuleService.create(operator, command);
    }

    /**
     * 更新规则。
     */
    public TicketAssignmentRule updateRule(CurrentUser operator, Long ruleId, TicketAssignmentRuleCommandDTO command) {
        return ticketAssignmentRuleService.update(operator, ruleId, command);
    }

    /**
     * 更新规则启用。
     */
    public TicketAssignmentRule updateRuleEnabled(CurrentUser operator, Long ruleId, boolean enabled) {
        return ticketAssignmentRuleService.updateEnabled(operator, ruleId, enabled);
    }

    /**
     * 获取规则。
     */
    public TicketAssignmentRule getRule(Long ruleId) {
        return ticketAssignmentRuleService.get(ruleId);
    }

    /**
     * 分页查询规则。
     */
    public PageResult<TicketAssignmentRule> pageRules(TicketAssignmentRulePageQueryDTO query) {
        return ticketAssignmentRuleService.page(query);
    }

    /**
     * 获取统计信息。
     */
    public TicketAssignmentStatsDTO stats() {
        return ticketAssignmentRuleService.stats();
    }

    /**
     * 预览分派结果。
     */
    public TicketAssignmentPreviewDTO preview(CurrentUser operator, Long ticketId) {
        return ticketAssignmentRuleService.preview(operator, ticketId);
    }

    /**
     * 执行分派。
     */
    public Ticket autoAssign(CurrentUser operator, Long ticketId) {
        return ticketAssignmentRuleService.autoAssign(operator, ticketId);
    }
}
