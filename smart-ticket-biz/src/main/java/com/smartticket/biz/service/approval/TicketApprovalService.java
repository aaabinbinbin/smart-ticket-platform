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
    // 支撑
    private final TicketServiceSupport support;
    // 工单审批仓储
    private final TicketApprovalRepository ticketApprovalRepository;
    // 工单审批步骤仓储
    private final TicketApprovalStepRepository ticketApprovalStepRepository;
    // 工单审批模板服务
    private final TicketApprovalTemplateService ticketApprovalTemplateService;
    // 工单审批步骤工厂
    private final TicketApprovalStepFactory ticketApprovalStepFactory;
    // 工单用户目录服务
    private final TicketUserDirectoryService ticketUserDirectoryService;
    // 工单详情缓存服务
    private final TicketDetailCacheService ticketDetailCacheService;

    /**
     * 构造工单审批服务。
     */
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

    /**
     * 获取审批。
     */
    public TicketApproval getApproval(CurrentUser operator, Long ticketId) {
        Ticket ticket = support.requireVisibleTicket(operator, ticketId);
        if (!requiresApproval(ticket)) {
            return null;
        }
        return enrichApproval(ticketApprovalRepository.findByTicketId(ticketId));
    }

    /**
     * 提交审批。
     */
    @Transactional
    public TicketApproval submitApproval(CurrentUser operator, Long ticketId, Long templateId, Long approverId, String submitComment) {
        Ticket ticket = support.requireTicket(ticketId);
        requireApprovalTicket(ticket);
        if (!operator.isAdmin() && !operator.getUserId().equals(ticket.getCreatorId())) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_FORBIDDEN, "仅管理员或工单创建人可以提交审批");
        }

        // 优先解析显式模板；没有模板时再尝试按工单类型命中启用中的默认模板。
        TicketApprovalTemplate template = resolveTemplate(ticket.getType(), templateId, approverId);
        List<TicketApprovalStep> steps = ticketApprovalStepFactory.build(ticketId, template, approverId);
        TicketApproval existing = ticketApprovalRepository.findByTicketId(ticketId);
        LocalDateTime now = LocalDateTime.now();
        Long firstApproverId = steps.get(0).getApproverId();

        if (existing == null) {
            // 首次提交时创建主审批单与步骤数据。
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
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批已通过，不能重复提交");
        } else if (existing.getApprovalStatus() == TicketApprovalStatusEnum.PENDING) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批正在进行中，不能重复提交");
        } else {
            // 被驳回后的再次提交会重置主单状态，并用新的步骤集覆盖旧步骤。
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
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.SUBMIT_APPROVAL, "提交审批", approvalSnapshot(existing), approvalSnapshot(after));
        ticketDetailCacheService.evict(ticketId);
        return after;
    }

    /**
     * 审批通过。
     */
    @Transactional
    public TicketApproval approve(CurrentUser operator, Long ticketId, String decisionComment) {
        return decide(operator, ticketId, true, decisionComment);
    }

    /**
     * 审批拒绝。
     */
    @Transactional
    public TicketApproval reject(CurrentUser operator, Long ticketId, String decisionComment) {
        return decide(operator, ticketId, false, decisionComment);
    }

    /**
     * 校验工单审批已通过。
     */
    public void requireApprovalPassed(Ticket ticket) {
        if (!requiresApproval(ticket)) {
            return;
        }
        TicketApproval approval = ticketApprovalRepository.findByTicketId(ticket.getId());
        if (approval == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_REQUIRED, "当前工单必须先提交审批");
        }
        if (approval.getApprovalStatus() != TicketApprovalStatusEnum.APPROVED) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_REQUIRED, "当前工单审批尚未通过");
        }
    }

    /**
     * 处理审批决策。
     */
    private TicketApproval decide(CurrentUser operator, Long ticketId, boolean approved, String decisionComment) {
        Ticket ticket = support.requireTicket(ticketId);
        requireApprovalTicket(ticket);
        TicketApproval before = ticketApprovalRepository.findByTicketId(ticketId);
        if (before == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_NOT_FOUND);
        }
        if (before.getApprovalStatus() != TicketApprovalStatusEnum.PENDING) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批当前不处于待处理状态");
        }

        TicketApprovalStep currentStep = ticketApprovalStepRepository.findCurrentPendingByTicketId(ticketId);
        if (currentStep == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "当前审批步骤不存在");
        }
        if (!operator.isAdmin() && !operator.getUserId().equals(currentStep.getApproverId())) {
            throw new BusinessException(BusinessErrorCode.TICKET_APPROVAL_FORBIDDEN, "只有当前审批人可以执行审批操作");
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
            // 任一节点驳回后，主审批单直接进入拒绝态，不再继续后续步骤。
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
            support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.REJECT, "审批拒绝", approvalSnapshot(before), approvalSnapshot(after));
            ticketDetailCacheService.evict(ticketId);
            return after;
        }

        TicketApprovalStep nextStep = ticketApprovalStepRepository.findNextWaitingByTicketId(ticketId, currentStep.getStepOrder());
        if (nextStep != null) {
            // 多级审批命中下一步时，只推进当前审批人，不结束整个审批单。
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
            // 没有后续步骤时，当前通过即代表整张审批单通过。
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
        support.writeLog(ticketId, operator.getUserId(), OperationTypeEnum.APPROVE, "审批通过", approvalSnapshot(before), approvalSnapshot(after));
        ticketDetailCacheService.evict(ticketId);
        return after;
    }

    /**
     * 解析模板。
     */
    private TicketApprovalTemplate resolveTemplate(TicketTypeEnum ticketType, Long templateId, Long approverId) {
        if (templateId != null) {
            TicketApprovalTemplate template = ticketApprovalTemplateService.get(templateId);
            if (!Integer.valueOf(1).equals(template.getEnabled())) {
                throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批模板已停用");
            }
            if (template.getTicketType() != ticketType) {
                throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "审批模板与工单类型不匹配");
            }
            return template;
        }
        TicketApprovalTemplate autoTemplate = ticketApprovalTemplateService.findEnabledTemplate(ticketType.getCode());
        if (autoTemplate != null) {
            return autoTemplate;
        }
        if (approverId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "未找到可用审批模板，且未指定审批人");
        }
        ticketUserDirectoryService.requireApproverUser(approverId);
        return null;
    }

    /**
     * 校验审批工单。
     */
    private void requireApprovalTicket(Ticket ticket) {
        if (!requiresApproval(ticket)) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_APPROVAL, "当前工单类型不需要审批");
        }
    }

    /**
     * 判断工单是否需要审批。
     */
    private boolean requiresApproval(Ticket ticket) {
        return ticket != null && (ticket.getType() == TicketTypeEnum.ACCESS_REQUEST || ticket.getType() == TicketTypeEnum.CHANGE_REQUEST);
    }

    /**
     * 补全审批模板和步骤信息。
     */
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

    /**
     * 生成审批快照。
     */
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
