package com.smartticket.biz.repository.approval;

import com.smartticket.domain.entity.TicketApprovalTemplate;
import com.smartticket.domain.entity.TicketApprovalTemplateStep;
import com.smartticket.domain.mapper.TicketApprovalTemplateMapper;
import com.smartticket.domain.mapper.TicketApprovalTemplateStepMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单审批模板仓储仓储接口。
 */
@Repository
public class TicketApprovalTemplateRepository {
    // 模板映射接口
    private final TicketApprovalTemplateMapper templateMapper;
    // 步骤映射接口
    private final TicketApprovalTemplateStepMapper stepMapper;

    /**
     * 构造工单审批模板仓储。
     */
    public TicketApprovalTemplateRepository(
            TicketApprovalTemplateMapper templateMapper,
            TicketApprovalTemplateStepMapper stepMapper
    ) {
        this.templateMapper = templateMapper;
        this.stepMapper = stepMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketApprovalTemplate template) {
        return templateMapper.insert(template);
    }

    /**
     * 更新。
     */
    public int update(TicketApprovalTemplate template) {
        return templateMapper.update(template);
    }

    /**
     * 查询按ID。
     */
    public TicketApprovalTemplate findById(Long id) {
        TicketApprovalTemplate template = templateMapper.findById(id);
        if (template != null) {
            template.setSteps(stepMapper.findByTemplateId(id));
        }
        return template;
    }

    /**
     * 查询启用按工单类型。
     */
    public TicketApprovalTemplate findEnabledByTicketType(String ticketType) {
        TicketApprovalTemplate template = templateMapper.findEnabledByTicketType(ticketType);
        if (template != null) {
            template.setSteps(stepMapper.findByTemplateId(template.getId()));
        }
        return template;
    }

    /**
     * 分页查询。
     */
    public List<TicketApprovalTemplate> page(String ticketType, Integer enabled, int offset, int limit) {
        List<TicketApprovalTemplate> templates = templateMapper.page(ticketType, enabled, offset, limit);
        for (TicketApprovalTemplate template : templates) {
            template.setSteps(stepMapper.findByTemplateId(template.getId()));
        }
        return templates;
    }

    /**
     * 获取统计信息。
     */
    public long count(String ticketType, Integer enabled) {
        return templateMapper.count(ticketType, enabled);
    }

    /**
     * 更新启用。
     */
    public int updateEnabled(Long id, Integer enabled) {
        return templateMapper.updateEnabled(id, enabled);
    }

    /**
     * 处理步骤信息。
     */
    public int replaceSteps(Long templateId, List<TicketApprovalTemplateStep> steps) {
        stepMapper.deleteByTemplateId(templateId);
        if (steps == null || steps.isEmpty()) {
            return 0;
        }
        return stepMapper.insertBatch(steps);
    }
}

