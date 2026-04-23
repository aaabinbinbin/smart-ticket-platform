package com.smartticket.biz.service.approval;

import com.smartticket.biz.dto.approval.TicketApprovalTemplateCommandDTO;
import com.smartticket.biz.dto.approval.TicketApprovalTemplatePageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketApprovalTemplate;
import org.springframework.stereotype.Service;

/**
 * 审批管理门面。
 *
 * <p>面向管理端暴露审批模板相关用例，避免 Controller 直接依赖模板服务内部实现。</p>
 */
@Service
public class TicketApprovalAdminService {
    private final TicketApprovalTemplateService ticketApprovalTemplateService;

    public TicketApprovalAdminService(TicketApprovalTemplateService ticketApprovalTemplateService) {
        this.ticketApprovalTemplateService = ticketApprovalTemplateService;
    }

    public TicketApprovalTemplate createTemplate(CurrentUser operator, TicketApprovalTemplateCommandDTO command) {
        return ticketApprovalTemplateService.create(operator, command);
    }

    public TicketApprovalTemplate updateTemplate(CurrentUser operator, Long templateId, TicketApprovalTemplateCommandDTO command) {
        return ticketApprovalTemplateService.update(operator, templateId, command);
    }

    public TicketApprovalTemplate updateTemplateEnabled(CurrentUser operator, Long templateId, boolean enabled) {
        return ticketApprovalTemplateService.updateEnabled(operator, templateId, enabled);
    }

    public TicketApprovalTemplate getTemplate(Long templateId) {
        return ticketApprovalTemplateService.get(templateId);
    }

    public PageResult<TicketApprovalTemplate> pageTemplates(TicketApprovalTemplatePageQueryDTO query) {
        return ticketApprovalTemplateService.page(query);
    }
}
