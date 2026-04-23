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
    private final TicketAssignmentRuleService ticketAssignmentRuleService;

    public TicketAssignmentService(TicketAssignmentRuleService ticketAssignmentRuleService) {
        this.ticketAssignmentRuleService = ticketAssignmentRuleService;
    }

    public TicketAssignmentRule createRule(CurrentUser operator, TicketAssignmentRuleCommandDTO command) {
        return ticketAssignmentRuleService.create(operator, command);
    }

    public TicketAssignmentRule updateRule(CurrentUser operator, Long ruleId, TicketAssignmentRuleCommandDTO command) {
        return ticketAssignmentRuleService.update(operator, ruleId, command);
    }

    public TicketAssignmentRule updateRuleEnabled(CurrentUser operator, Long ruleId, boolean enabled) {
        return ticketAssignmentRuleService.updateEnabled(operator, ruleId, enabled);
    }

    public TicketAssignmentRule getRule(Long ruleId) {
        return ticketAssignmentRuleService.get(ruleId);
    }

    public PageResult<TicketAssignmentRule> pageRules(TicketAssignmentRulePageQueryDTO query) {
        return ticketAssignmentRuleService.page(query);
    }

    public TicketAssignmentStatsDTO stats() {
        return ticketAssignmentRuleService.stats();
    }

    public TicketAssignmentPreviewDTO preview(CurrentUser operator, Long ticketId) {
        return ticketAssignmentRuleService.preview(operator, ticketId);
    }

    public Ticket autoAssign(CurrentUser operator, Long ticketId) {
        return ticketAssignmentRuleService.autoAssign(operator, ticketId);
    }
}
