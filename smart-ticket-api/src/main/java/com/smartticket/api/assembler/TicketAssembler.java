package com.smartticket.api.assembler;

import com.smartticket.api.vo.ticket.TicketApprovalStepVO;
import com.smartticket.api.vo.ticket.TicketApprovalVO;
import com.smartticket.api.vo.ticket.TicketCommentVO;
import com.smartticket.api.vo.ticket.TicketDetailVO;
import com.smartticket.api.vo.ticket.TicketOperationLogVO;
import com.smartticket.api.vo.ticket.TicketSummaryBundleVO;
import com.smartticket.api.vo.ticket.TicketSummaryVO;
import com.smartticket.api.vo.ticket.TicketVO;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketSummaryBundleDTO;
import com.smartticket.biz.dto.TicketSummaryDTO;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.entity.TicketApprovalStep;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import org.springframework.stereotype.Component;

@Component
public class TicketAssembler {

    public TicketVO toVO(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return TicketVO.builder()
                .id(ticket.getId())
                .ticketNo(ticket.getTicketNo())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .type(ticket.getType() == null ? null : ticket.getType().getCode())
                .typeInfo(ticket.getType() == null ? null : ticket.getType().getInfo())
                .typeProfile(ticket.getTypeProfile())
                .category(ticket.getCategory() == null ? null : ticket.getCategory().getCode())
                .categoryInfo(ticket.getCategory() == null ? null : ticket.getCategory().getInfo())
                .priority(ticket.getPriority() == null ? null : ticket.getPriority().getCode())
                .priorityInfo(ticket.getPriority() == null ? null : ticket.getPriority().getInfo())
                .status(ticket.getStatus() == null ? null : ticket.getStatus().getCode())
                .statusInfo(ticket.getStatus() == null ? null : ticket.getStatus().getInfo())
                .creatorId(ticket.getCreatorId())
                .assigneeId(ticket.getAssigneeId())
                .groupId(ticket.getGroupId())
                .queueId(ticket.getQueueId())
                .solutionSummary(ticket.getSolutionSummary())
                .source(ticket.getSource())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    public TicketApprovalVO toApprovalVO(TicketApproval approval) {
        if (approval == null) {
            return null;
        }
        return TicketApprovalVO.builder()
                .id(approval.getId())
                .ticketId(approval.getTicketId())
                .templateId(approval.getTemplateId())
                .currentStepOrder(approval.getCurrentStepOrder())
                .approvalStatus(approval.getApprovalStatus() == null ? null : approval.getApprovalStatus().getCode())
                .approvalStatusInfo(approval.getApprovalStatus() == null ? null : approval.getApprovalStatus().getInfo())
                .approverId(approval.getApproverId())
                .requestedBy(approval.getRequestedBy())
                .submitComment(approval.getSubmitComment())
                .decisionComment(approval.getDecisionComment())
                .submittedAt(approval.getSubmittedAt())
                .decidedAt(approval.getDecidedAt())
                .createdAt(approval.getCreatedAt())
                .updatedAt(approval.getUpdatedAt())
                .steps(approval.getSteps() == null ? null : approval.getSteps().stream().map(this::toApprovalStepVO).toList())
                .build();
    }

    public TicketApprovalStepVO toApprovalStepVO(TicketApprovalStep step) {
        if (step == null) {
            return null;
        }
        return TicketApprovalStepVO.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .stepName(step.getStepName())
                .approverId(step.getApproverId())
                .stepStatus(step.getStepStatus() == null ? null : step.getStepStatus().getCode())
                .stepStatusInfo(step.getStepStatus() == null ? null : step.getStepStatus().getInfo())
                .decisionComment(step.getDecisionComment())
                .decidedAt(step.getDecidedAt())
                .createdAt(step.getCreatedAt())
                .updatedAt(step.getUpdatedAt())
                .build();
    }

    public TicketCommentVO toCommentVO(TicketComment comment) {
        if (comment == null) {
            return null;
        }
        return TicketCommentVO.builder()
                .id(comment.getId())
                .ticketId(comment.getTicketId())
                .commenterId(comment.getCommenterId())
                .commentType(comment.getCommentType())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    public TicketOperationLogVO toLogVO(TicketOperationLog log) {
        if (log == null) {
            return null;
        }
        return TicketOperationLogVO.builder()
                .id(log.getId())
                .ticketId(log.getTicketId())
                .operatorId(log.getOperatorId())
                .operationType(log.getOperationType() == null ? null : log.getOperationType().getCode())
                .operationTypeInfo(log.getOperationType() == null ? null : log.getOperationType().getInfo())
                .operationDesc(log.getOperationDesc())
                .beforeValue(log.getBeforeValue())
                .afterValue(log.getAfterValue())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public TicketDetailVO toDetailVO(TicketDetailDTO detail) {
        return TicketDetailVO.builder()
                .ticket(toVO(detail.getTicket()))
                .approval(toApprovalVO(detail.getApproval()))
                .comments(detail.getComments().stream().map(this::toCommentVO).toList())
                .operationLogs(detail.getOperationLogs().stream().map(this::toLogVO).toList())
                .summaries(toSummaryBundleVO(detail.getSummaries()))
                .build();
    }

    public TicketSummaryVO toSummaryVO(TicketSummaryDTO summary) {
        if (summary == null) {
            return null;
        }
        return TicketSummaryVO.builder()
                .view(summary.getView() == null ? null : summary.getView().getCode())
                .viewInfo(summary.getView() == null ? null : summary.getView().getInfo())
                .title(summary.getTitle())
                .summary(summary.getSummary())
                .highlights(summary.getHighlights())
                .riskLevel(summary.getRiskLevel())
                .generatedAt(summary.getGeneratedAt())
                .build();
    }

    public TicketSummaryBundleVO toSummaryBundleVO(TicketSummaryBundleDTO bundle) {
        if (bundle == null) {
            return null;
        }
        return TicketSummaryBundleVO.builder()
                .submitterSummary(toSummaryVO(bundle.getSubmitterSummary()))
                .assigneeSummary(toSummaryVO(bundle.getAssigneeSummary()))
                .adminSummary(toSummaryVO(bundle.getAdminSummary()))
                .build();
    }
}
