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
    // 工单审批模板服务
    private final TicketApprovalTemplateService ticketApprovalTemplateService;

    /**
     * 构造工单审批管理服务。
     */
    public TicketApprovalAdminService(TicketApprovalTemplateService ticketApprovalTemplateService) {
        this.ticketApprovalTemplateService = ticketApprovalTemplateService;
    }

    /**
     * 创建模板。
     */
    public TicketApprovalTemplate createTemplate(CurrentUser operator, TicketApprovalTemplateCommandDTO command) {
        return ticketApprovalTemplateService.create(operator, command);
    }

    /**
     * 更新模板。
     */
    public TicketApprovalTemplate updateTemplate(CurrentUser operator, Long templateId, TicketApprovalTemplateCommandDTO command) {
        return ticketApprovalTemplateService.update(operator, templateId, command);
    }

    /**
     * 更新模板启用。
     */
    public TicketApprovalTemplate updateTemplateEnabled(CurrentUser operator, Long templateId, boolean enabled) {
        return ticketApprovalTemplateService.updateEnabled(operator, templateId, enabled);
    }

    /**
     * 获取模板。
     */
    public TicketApprovalTemplate getTemplate(Long templateId) {
        return ticketApprovalTemplateService.get(templateId);
    }

    /**
     * 分页查询模板。
     */
    public PageResult<TicketApprovalTemplate> pageTemplates(TicketApprovalTemplatePageQueryDTO query) {
        return ticketApprovalTemplateService.page(query);
    }
}
