package com.smartticket.biz.service.approval;

import com.smartticket.biz.dto.approval.TicketApprovalTemplateCommandDTO;
import com.smartticket.biz.dto.approval.TicketApprovalTemplatePageQueryDTO;
import com.smartticket.biz.dto.approval.TicketApprovalTemplateStepCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.approval.TicketApprovalTemplateRepository;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.biz.service.ticket.TicketUserDirectoryService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketApprovalTemplate;
import com.smartticket.domain.entity.TicketApprovalTemplateStep;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责审批模板管理。
 * 这里聚焦模板自身的合法性校验和步骤装配，不承载具体审批执行逻辑。
 */
@Service
public class TicketApprovalTemplateService {
    // 仓储
    private final TicketApprovalTemplateRepository repository;
    // 权限服务
    private final TicketPermissionService permissionService;
    // 工单用户目录服务
    private final TicketUserDirectoryService ticketUserDirectoryService;

    /**
     * 构造工单审批模板服务。
     */
    public TicketApprovalTemplateService(
            TicketApprovalTemplateRepository repository,
            TicketPermissionService permissionService,
            TicketUserDirectoryService ticketUserDirectoryService
    ) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.ticketUserDirectoryService = ticketUserDirectoryService;
    }

    /**
     * 创建。
     */
    @Transactional
    public TicketApprovalTemplate create(CurrentUser operator, TicketApprovalTemplateCommandDTO command) {
        permissionService.requireAdmin(operator);
        validateCommand(command);
        TicketApprovalTemplate template = applyTemplateFields(TicketApprovalTemplate.builder().build(), command);
        repository.insert(template);
        repository.replaceSteps(template.getId(), buildSteps(template.getId(), command.getSteps()));
        return repository.findById(template.getId());
    }

    /**
     * 更新。
     */
    @Transactional
    public TicketApprovalTemplate update(CurrentUser operator, Long templateId, TicketApprovalTemplateCommandDTO command) {
        permissionService.requireAdmin(operator);
        TicketApprovalTemplate existing = requireById(templateId);
        validateCommand(command);
        applyTemplateFields(existing, command);
        repository.update(existing);
        repository.replaceSteps(templateId, buildSteps(templateId, command.getSteps()));
        return repository.findById(templateId);
    }

    /**
     * 更新启用。
     */
    @Transactional
    public TicketApprovalTemplate updateEnabled(CurrentUser operator, Long templateId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requireById(templateId);
        repository.updateEnabled(templateId, toEnabled(enabled));
        return repository.findById(templateId);
    }

    /**
     * 获取详情。
     */
    public TicketApprovalTemplate get(Long templateId) {
        return requireById(templateId);
    }

    /**
     * 查询启用模板。
     */
    public TicketApprovalTemplate findEnabledTemplate(String ticketType) {
        return repository.findEnabledByTicketType(ticketType);
    }

    /**
     * 分页查询。
     */
    public PageResult<TicketApprovalTemplate> page(TicketApprovalTemplatePageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        int offset = (pageNo - 1) * pageSize;
        String ticketType = query.getTicketType() == null ? null : query.getTicketType().getCode();
        Integer enabled = query.getEnabled() == null ? null : toEnabled(query.getEnabled());
        List<TicketApprovalTemplate> records = repository.page(ticketType, enabled, offset, pageSize);
        long total = repository.count(ticketType, enabled);
        return PageResult.<TicketApprovalTemplate>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    /**
     * 校验按ID。
     */
    private TicketApprovalTemplate requireById(Long templateId) {
        TicketApprovalTemplate template = repository.findById(templateId);
        if (template == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    /**
     * 校验命令。
     */
    private void validateCommand(TicketApprovalTemplateCommandDTO command) {
        if (command.getTicketType() == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批模板必须绑定工单类型");
        }
        if (command.getSteps() == null || command.getSteps().isEmpty()) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批模板至少需要一个步骤");
        }
        List<TicketApprovalTemplateStepCommandDTO> sorted = sortSteps(command.getSteps());
        int expected = 1;
        for (TicketApprovalTemplateStepCommandDTO step : sorted) {
            if (step.getStepOrder() == null || step.getStepOrder() != expected) {
                throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批步骤顺序必须从 1 开始连续递增");
            }
            ticketUserDirectoryService.requireApproverUser(step.getApproverId());
            expected++;
        }
    }

    /**
     * 构建Steps。
     */
    private List<TicketApprovalTemplateStep> buildSteps(Long templateId, List<TicketApprovalTemplateStepCommandDTO> steps) {
        return sortSteps(steps).stream()
                .map(step -> TicketApprovalTemplateStep.builder()
                        .templateId(templateId)
                        .stepOrder(step.getStepOrder())
                        .stepName(step.getStepName())
                        .approverId(step.getApproverId())
                        .build())
                .toList();
    }

    /**
     * 应用模板Fields。
     */
    private TicketApprovalTemplate applyTemplateFields(
            TicketApprovalTemplate template,
            TicketApprovalTemplateCommandDTO command
    ) {
        template.setTemplateName(command.getTemplateName().trim());
        template.setTicketType(command.getTicketType());
        template.setDescription(command.getDescription());
        template.setEnabled(toEnabled(command.getEnabled()));
        return template;
    }

    /**
     * 处理步骤信息。
     */
    private List<TicketApprovalTemplateStepCommandDTO> sortSteps(List<TicketApprovalTemplateStepCommandDTO> steps) {
        return steps.stream()
                .sorted(Comparator.comparing(TicketApprovalTemplateStepCommandDTO::getStepOrder))
                .toList();
    }

    /**
     * 转换为启用。
     */
    private int toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }
}
