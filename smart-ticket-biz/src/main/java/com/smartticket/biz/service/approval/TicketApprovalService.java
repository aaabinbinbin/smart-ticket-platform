package com.smartticket.biz.service.approval;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.approval.TicketApprovalRepository;
import com.smartticket.biz.repository.approval.TicketApprovalStepRepository;
import com.smartticket.biz.service.ticket.TicketDetailCacheService;
import com.smartticket.biz.service.ticket.TicketServiceSupport;
import com.smartticket.biz.service.ticket.TicketUserDirectoryService;
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

/**
 * 工单审批用例服务。
 *
 * <p>这里负责审批提交、通过、驳回等流程编排。
 * 模板管理、步骤装配、审批人校验分别由独立组件处理，避免本类继续膨胀。</p>
 */
@Service
public class TicketApprovalService {
    private final TicketServiceSupport support;
    private final TicketApprovalRepository ticketApprovalRepository;
    private final TicketApprovalStepRepository ticketApprovalStepRepository;
    private final TicketApprovalTemplateService ticketApprovalTemplateService;
    private final TicketApprovalStepFactory ticketApprovalStepFactory;
    private final TicketUserDirectoryService ticketUserDirectoryService;
    private final TicketDetailCacheService ticketDetailCacheService;

    public TicketApprovalService(
            TicketServiceSupport support,
            TicketApprovalRepository ticketApprovalRepository,
            TicketApprovalStepRepository ticketApprovalStepRepository,
            TicketApprovalTemplateService ticketApprovalTemplateService,
            TicketApprovalStepFactory ticketApprovalStepFactory,
            TicketUserDirectoryService ticketUserDirectoryService,
            TicketDetailCacheService ticketDetailCacheService
    ) {
        this.support = support;
        this.ticketApprovalRepository = ticketApprovalRepository;
        this.ticketApprovalStepRepository = ticketApprovalStepRepository;
        this.ticketApprovalTemplateService = ticketApprovalTemplateService;
        this.ticketApprovalStepFactory = ticketApprovalStepFactory;
        this.ticketUserDirectoryService = ticketUserDirectoryService;
        this.ticketDetailCacheService = ticketDetailCacheService;
    }

    public TicketApproval getApproval(CurrentUser operator, Long ticketId) {
        Ticket ticket = support.requireVisibleTicket(operator, ticketId);
        if (!requiresApproval(ticket)) {
            return null;
        }
        return enrichApproval(ticketApprovalRepository.findByTicketId(ticketId));
    }

    @Transactional
    public TicketApproval submitApproval(CurrentUser operator, Long ticketId, Long templateId, Long approverId, String submitComment) {
        Ticket ticket = support.requireTicket(ticketId);
        requireApprovalTicket(ticket);
        if (!operator.isAdmin() && !operator.getUserId().equals(ticket.getCreatorId())) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_FORBIDDEN, "Only admin or creator can submit approval");
        }

        TicketApprovalTemplate template = resolveTemplate(ticket.getType(), templateId, approverId);
        List<TicketApprovalStep> steps = ticketApprovalStepFactory.build(ticketId, template, approverId);
        TicketApproval existing = ticketApprovalRepository.findByTicketId(ticketId);
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
            ticketApprovalRepository.insert(approval);
            ticketApprovalStepFactory.assignApprovalId(steps, approval.getId());
            ticketApprovalStepRepository.insertBatch(steps);
        } else if (existing.getApprovalStatus() == TicketApprovalStatusEnum.APPROVED) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "Approval already passed");
        } else if (existing.getApprovalStatus() == TicketApprovalStatusEnum.PENDING) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "Approval is already pending");
        } else {
            support.requireUpdated(ticketApprovalRepository.updateForResubmit(
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
            TicketApproval approval = ticketApprovalRepository.findByTicketId(ticketId);
            ticketApprovalStepFactory.assignApprovalId(steps, approval.getId());
            ticketApprovalStepRepository.insertBatch(steps);
        }

        TicketApproval after = enrichApproval(ticketApprovalRepository.findByTicketId(ticketId));
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.SUBMIT_APPROVAL, "Submit approval", approvalSnapshot(existing), approvalSnapshot(after));
        ticketDetailCacheService.evict(ticketId);
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
        TicketApproval approval = ticketApprovalRepository.findByTicketId(ticket.getId());
        if (approval == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_REQUIRED, "Approval submission is required");
        }
        if (approval.getApprovalStatus() != TicketApprovalStatusEnum.APPROVED) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_REQUIRED, "Approval has not passed yet");
        }
    }

    private TicketApproval decide(CurrentUser operator, Long ticketId, boolean approved, String decisionComment) {
        Ticket ticket = support.requireTicket(ticketId);
        requireApprovalTicket(ticket);
        TicketApproval before = ticketApprovalRepository.findByTicketId(ticketId);
        if (before == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_NOT_FOUND);
        }
        if (before.getApprovalStatus() != TicketApprovalStatusEnum.PENDING) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "Approval is not in pending state");
        }

        TicketApprovalStep currentStep = ticketApprovalStepRepository.findCurrentPendingByTicketId(ticketId);
        if (currentStep == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "Current approval step is missing");
        }
        if (!operator.isAdmin() && !operator.getUserId().equals(currentStep.getApproverId())) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_FORBIDDEN, "Only current approver can make the decision");
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
            support.requireUpdated(ticketApprovalRepository.updateDecision(
                    ticketId,
                    TicketApprovalStatusEnum.PENDING,
                    TicketApprovalStatusEnum.REJECTED,
                    currentStep.getStepOrder(),
                    currentStep.getApproverId(),
                    decisionComment,
                    now
            ));
            TicketApproval after = enrichApproval(ticketApprovalRepository.findByTicketId(ticketId));
            support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.REJECT, "Reject approval", approvalSnapshot(before), approvalSnapshot(after));
            ticketDetailCacheService.evict(ticketId);
            return after;
        }

        TicketApprovalStep nextStep = ticketApprovalStepRepository.findNextWaitingByTicketId(ticketId, currentStep.getStepOrder());
        if (nextStep != null) {
            support.requireUpdated(ticketApprovalStepRepository.activateStep(
                    nextStep.getId(),
                    TicketApprovalStepStatusEnum.WAITING,
                    TicketApprovalStepStatusEnum.PENDING
            ));
            support.requireUpdated(ticketApprovalRepository.updateDecision(
                    ticketId,
                    TicketApprovalStatusEnum.PENDING,
                    TicketApprovalStatusEnum.PENDING,
                    nextStep.getStepOrder(),
                    nextStep.getApproverId(),
                    decisionComment,
                    null
            ));
        } else {
            support.requireUpdated(ticketApprovalRepository.updateDecision(
                    ticketId,
                    TicketApprovalStatusEnum.PENDING,
                    TicketApprovalStatusEnum.APPROVED,
                    currentStep.getStepOrder(),
                    currentStep.getApproverId(),
                    decisionComment,
                    now
            ));
        }

        TicketApproval after = enrichApproval(ticketApprovalRepository.findByTicketId(ticketId));
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.APPROVE, "Approve approval", approvalSnapshot(before), approvalSnapshot(after));
        ticketDetailCacheService.evict(ticketId);
        return after;
    }

    private TicketApprovalTemplate resolveTemplate(TicketTypeEnum ticketType, Long templateId, Long approverId) {
        if (templateId != null) {
            TicketApprovalTemplate template = ticketApprovalTemplateService.get(templateId);
            if (!Integer.valueOf(1).equals(template.getEnabled())) {
                throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "Approval template is disabled");
            }
            if (template.getTicketType() != ticketType) {
                throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "Approval template does not match ticket type");
            }
            return template;
        }
        TicketApprovalTemplate autoTemplate = ticketApprovalTemplateService.findEnabledTemplate(ticketType.getCode());
        if (autoTemplate != null) {
            return autoTemplate;
        }
        if (approverId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "No enabled template found and no approver specified");
        }
        ticketUserDirectoryService.requireApproverUser(approverId);
        return null;
    }

    private void requireApprovalTicket(Ticket ticket) {
        if (!requiresApproval(ticket)) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "Current ticket type does not require approval");
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
