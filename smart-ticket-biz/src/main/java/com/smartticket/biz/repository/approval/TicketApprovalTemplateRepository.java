package com.smartticket.biz.repository.approval;

import com.smartticket.domain.entity.TicketApprovalTemplate;
import com.smartticket.domain.entity.TicketApprovalTemplateStep;
import com.smartticket.domain.mapper.TicketApprovalTemplateMapper;
import com.smartticket.domain.mapper.TicketApprovalTemplateStepMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TicketApprovalTemplateRepository {
    private final TicketApprovalTemplateMapper templateMapper;
    private final TicketApprovalTemplateStepMapper stepMapper;

    public TicketApprovalTemplateRepository(
            TicketApprovalTemplateMapper templateMapper,
            TicketApprovalTemplateStepMapper stepMapper
    ) {
        this.templateMapper = templateMapper;
        this.stepMapper = stepMapper;
    }

    public int insert(TicketApprovalTemplate template) {
        return templateMapper.insert(template);
    }

    public int update(TicketApprovalTemplate template) {
        return templateMapper.update(template);
    }

    public TicketApprovalTemplate findById(Long id) {
        TicketApprovalTemplate template = templateMapper.findById(id);
        if (template != null) {
            template.setSteps(stepMapper.findByTemplateId(id));
        }
        return template;
    }

    public TicketApprovalTemplate findEnabledByTicketType(String ticketType) {
        TicketApprovalTemplate template = templateMapper.findEnabledByTicketType(ticketType);
        if (template != null) {
            template.setSteps(stepMapper.findByTemplateId(template.getId()));
        }
        return template;
    }

    public List<TicketApprovalTemplate> page(String ticketType, Integer enabled, int offset, int limit) {
        List<TicketApprovalTemplate> templates = templateMapper.page(ticketType, enabled, offset, limit);
        for (TicketApprovalTemplate template : templates) {
            template.setSteps(stepMapper.findByTemplateId(template.getId()));
        }
        return templates;
    }

    public long count(String ticketType, Integer enabled) {
        return templateMapper.count(ticketType, enabled);
    }

    public int updateEnabled(Long id, Integer enabled) {
        return templateMapper.updateEnabled(id, enabled);
    }

    public int replaceSteps(Long templateId, List<TicketApprovalTemplateStep> steps) {
        stepMapper.deleteByTemplateId(templateId);
        if (steps == null || steps.isEmpty()) {
            return 0;
        }
        return stepMapper.insertBatch(steps);
    }
}

