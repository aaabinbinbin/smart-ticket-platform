package com.smartticket.biz.service.approval;

import com.smartticket.domain.entity.TicketApprovalStep;
import com.smartticket.domain.entity.TicketApprovalTemplate;
import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 审批步骤工厂。
 *
 * <p>把模板步骤装配、直审单步装配以及 approvalId 回填集中放在一起，
 * 避免审批服务同时承担流程编排和对象创建两类职责。</p>
 */
@Component
public class TicketApprovalStepFactory {

    public List<TicketApprovalStep> build(Long ticketId, TicketApprovalTemplate template, Long approverId) {
        if (template == null) {
            return List.of(TicketApprovalStep.builder()
                    .ticketId(ticketId)
                    .stepOrder(1)
                    .stepName("Direct Approval")
                    .approverId(approverId)
                    .stepStatus(TicketApprovalStepStatusEnum.PENDING)
                    .build());
        }
        return template.getSteps().stream()
                .map(step -> TicketApprovalStep.builder()
                        .ticketId(ticketId)
                        .stepOrder(step.getStepOrder())
                        .stepName(step.getStepName())
                        .approverId(step.getApproverId())
                        .stepStatus(step.getStepOrder() == 1 ? TicketApprovalStepStatusEnum.PENDING : TicketApprovalStepStatusEnum.WAITING)
                        .build())
                .toList();
    }

    public void assignApprovalId(List<TicketApprovalStep> steps, Long approvalId) {
        for (TicketApprovalStep step : steps) {
            step.setApprovalId(approvalId);
        }
    }
}
