package com.smartticket.biz.service.approval;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.approval.TicketApprovalStepRepository;
import com.smartticket.biz.service.ticket.TicketServiceSupport;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.entity.TicketApprovalStep;
import com.smartticket.domain.entity.TicketApprovalTemplate;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketApprovalService {
    private final TicketServiceSupport support;
    private final TicketApprovalTemplateService ticketApprovalTemplateService;
    private final TicketApprovalStepRepository ticketApprovalStepRepository;

    public TicketApprovalService(
            TicketServiceSupport support,
            TicketApprovalTemplateService ticketApprovalTemplateService,
            TicketApprovalStepRepository ticketApprovalStepRepository
    ) {
        this.support = support;
        this.ticketApprovalTemplateService = ticketApprovalTemplateService;
        this.ticketApprovalStepRepository = ticketApprovalStepRepository;
    }

    public TicketApproval getApproval(CurrentUser operator, Long ticketId) {
        Ticket ticket = support.requireVisibleTicket(operator, ticketId);
        if (!requiresApproval(ticket)) {
            return null;
        }
        return enrichApproval(support.ticketApprovalRepository().findByTicketId(ticketId));
    }

    @Transactional
    public TicketApproval submitApproval(CurrentUser operator, Long ticketId, Long templateId, Long approverId, String submitComment) {
        Ticket ticket = support.requireTicket(ticketId);
        requireApprovalTicket(ticket);
        if (!operator.isAdmin() && !operator.getUserId().equals(ticket.getCreatorId())) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_FORBIDDEN, "ֻ���ᵥ�˻����Ա�����ύ����");
        }

        TicketApprovalTemplate template = resolveTemplate(ticket.getType(), templateId, approverId);
        List<TicketApprovalStep> steps = buildApprovalSteps(ticketId, template, approverId);
        TicketApproval existing = support.ticketApprovalRepository().findByTicketId(ticketId);
        LocalDateTime now = LocalDateTime.now();
        Long firstApproverId = steps.get(0).getApproverId();

        if (existing == null) {
            TicketApproval approval = TicketApproval.builder()
                    .ticketId(ticketId)
                    .templateId(template == null ? null : template.getId())
                    .currentStepOrder(1)
                    .approvalStatus(TicketApprovalStatusEnum.PENDING)
                    .approverId(firstApproverId)
                    .requestedBy(operator.getUserId())
                    .submitComment(submitComment)
                    .submittedAt(now)
                    .build();
            support.ticketApprovalRepository().insert(approval);
            assignApprovalId(steps, approval.getId());
            ticketApprovalStepRepository.insertBatch(steps);
        } else if (existing.getApprovalStatus() == TicketApprovalStatusEnum.APPROVED) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "�ù���������ͨ���������ظ��ύ");
        } else if (existing.getApprovalStatus() == TicketApprovalStatusEnum.PENDING) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "�ù������д�������¼");
        } else {
            support.requireUpdated(support.ticketApprovalRepository().updateForResubmit(
                    ticketId,
                    template == null ? null : template.getId(),
                    1,
                    TicketApprovalStatusEnum.PENDING,
                    firstApproverId,
                    operator.getUserId(),
                    submitComment,
                    now
            ));
            ticketApprovalStepRepository.deleteByTicketId(ticketId);
            TicketApproval approval = support.ticketApprovalRepository().findByTicketId(ticketId);
            assignApprovalId(steps, approval.getId());
            ticketApprovalStepRepository.insertBatch(steps);
        }

        TicketApproval after = enrichApproval(support.ticketApprovalRepository().findByTicketId(ticketId));
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.SUBMIT_APPROVAL, "�ύ��������", approvalSnapshot(existing), approvalSnapshot(after));
        support.ticketDetailCacheService().evict(ticketId);
        return after;
    }

    @Transactional
    public TicketApproval approve(CurrentUser operator, Long ticketId, String decisionComment) {
        return decide(operator, ticketId, true, decisionComment);
    }

    @Transactional
    public TicketApproval reject(CurrentUser operator, Long ticketId, String decisionComment) {
        return decide(operator, ticketId, false, decisionComment);
    }

    public void requireApprovalPassed(Ticket ticket) {
        if (!requiresApproval(ticket)) {
            return;
        }
        TicketApproval approval = support.ticketApprovalRepository().findByTicketId(ticket.getId());
        if (approval == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_REQUIRED, "�ù�����Ҫ���ύ����");
        }
        if (approval.getApprovalStatus() != TicketApprovalStatusEnum.APPROVED) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_REQUIRED, "�ù���������δͨ��");
        }
    }

    private TicketApproval decide(CurrentUser operator, Long ticketId, boolean approved, String decisionComment) {
        Ticket ticket = support.requireTicket(ticketId);
        requireApprovalTicket(ticket);
        TicketApproval before = support.ticketApprovalRepository().findByTicketId(ticketId);
        if (before == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_NOT_FOUND);
        }
        if (before.getApprovalStatus() != TicketApprovalStatusEnum.PENDING) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "��ǰ������¼���Ǵ�����״̬");
        }

        TicketApprovalStep currentStep = ticketApprovalStepRepository.findCurrentPendingByTicketId(ticketId);
        if (currentStep == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "��ǰ�����ڴ�������������");
        }
        if (!operator.isAdmin() && !operator.getUserId().equals(currentStep.getApproverId())) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_FORBIDDEN, "ֻ�е�ǰ���������˻����Ա����ִ������");
        }

        LocalDateTime now = LocalDateTime.now();
        support.requireUpdated(ticketApprovalStepRepository.updateDecision(
                currentStep.getId(),
                TicketApprovalStepStatusEnum.PENDING,
                approved ? TicketApprovalStepStatusEnum.APPROVED : TicketApprovalStepStatusEnum.REJECTED,
                decisionComment,
                now
        ));

        if (!approved) {
            support.requireUpdated(support.ticketApprovalRepository().updateDecision(
                    ticketId,
                    TicketApprovalStatusEnum.PENDING,
                    TicketApprovalStatusEnum.REJECTED,
                    currentStep.getStepOrder(),
                    currentStep.getApproverId(),
                    decisionComment,
                    now
            ));
            TicketApproval after = enrichApproval(support.ticketApprovalRepository().findByTicketId(ticketId));
            support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.REJECT, "��������", approvalSnapshot(before), approvalSnapshot(after));
            support.ticketDetailCacheService().evict(ticketId);
            return after;
        }

        TicketApprovalStep nextStep = ticketApprovalStepRepository.findNextWaitingByTicketId(ticketId, currentStep.getStepOrder());
        if (nextStep != null) {
            support.requireUpdated(ticketApprovalStepRepository.activateStep(
                    nextStep.getId(),
                    TicketApprovalStepStatusEnum.WAITING,
                    TicketApprovalStepStatusEnum.PENDING
            ));
            support.requireUpdated(support.ticketApprovalRepository().updateDecision(
                    ticketId,
                    TicketApprovalStatusEnum.PENDING,
                    TicketApprovalStatusEnum.PENDING,
                    nextStep.getStepOrder(),
                    nextStep.getApproverId(),
                    decisionComment,
                    null
            ));
        } else {
            support.requireUpdated(support.ticketApprovalRepository().updateDecision(
                    ticketId,
                    TicketApprovalStatusEnum.PENDING,
                    TicketApprovalStatusEnum.APPROVED,
                    currentStep.getStepOrder(),
                    currentStep.getApproverId(),
                    decisionComment,
                    now
            ));
        }

        TicketApproval after = enrichApproval(support.ticketApprovalRepository().findByTicketId(ticketId));
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.APPROVE, "����ͨ��", approvalSnapshot(before), approvalSnapshot(after));
        support.ticketDetailCacheService().evict(ticketId);
        return after;
    }

    private TicketApprovalTemplate resolveTemplate(TicketTypeEnum ticketType, Long templateId, Long approverId) {
        if (templateId != null) {
            TicketApprovalTemplate template = ticketApprovalTemplateService.get(templateId);
            if (!Integer.valueOf(1).equals(template.getEnabled())) {
                throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "����ģ����ͣ��");
            }
            if (template.getTicketType() != ticketType) {
                throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "����ģ���빤�����Ͳ�ƥ��");
            }
            return template;
        }
        TicketApprovalTemplate autoTemplate = ticketApprovalTemplateService.findEnabledTemplate(ticketType.getCode());
        if (autoTemplate != null) {
            return autoTemplate;
        }
        if (approverId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "��ǰ����δ��������ģ�壬��δָ��������");
        }
        support.requireApproverUser(approverId);
        return null;
    }

    private List<TicketApprovalStep> buildApprovalSteps(Long ticketId, TicketApprovalTemplate template, Long approverId) {
        if (template == null) {
            return List.of(TicketApprovalStep.builder()
                    .ticketId(ticketId)
                    .stepOrder(1)
                    .stepName("�˹�����")
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

    private void assignApprovalId(List<TicketApprovalStep> steps, Long approvalId) {
        for (TicketApprovalStep step : steps) {
            step.setApprovalId(approvalId);
        }
    }

    private void requireApprovalTicket(Ticket ticket) {
        if (!requiresApproval(ticket)) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "��ǰ�������Ͳ���Ҫ����");
        }
    }

    private boolean requiresApproval(Ticket ticket) {
        return ticket != null && (ticket.getType() == TicketTypeEnum.ACCESS_REQUEST || ticket.getType() == TicketTypeEnum.CHANGE_REQUEST);
    }

    private TicketApproval enrichApproval(TicketApproval approval) {
        if (approval == null) {
            return null;
        }
        if (approval.getTemplateId() != null) {
            approval.setTemplate(ticketApprovalTemplateService.get(approval.getTemplateId()));
        }
        approval.setSteps(ticketApprovalStepRepository.findByTicketId(approval.getTicketId()));
        return approval;
    }

    private String approvalSnapshot(TicketApproval approval) {
        if (approval == null) {
            return null;
        }
        return "ticketId=" + approval.getTicketId()
                + ", templateId=" + approval.getTemplateId()
                + ", currentStepOrder=" + approval.getCurrentStepOrder()
                + ", approvalStatus=" + support.enumCode(approval.getApprovalStatus())
                + ", approverId=" + approval.getApproverId()
                + ", requestedBy=" + approval.getRequestedBy()
                + ", submitComment=" + approval.getSubmitComment()
                + ", decisionComment=" + approval.getDecisionComment();
    }
}

