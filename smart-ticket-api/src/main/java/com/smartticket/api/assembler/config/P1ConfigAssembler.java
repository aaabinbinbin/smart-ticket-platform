package com.smartticket.api.assembler.config;

import com.smartticket.api.vo.approval.TicketApprovalTemplateStepVO;
import com.smartticket.api.vo.approval.TicketApprovalTemplateVO;
import com.smartticket.api.vo.assignment.TicketAssignmentPreviewVO;
import com.smartticket.api.vo.assignment.TicketAssignmentRuleVO;
import com.smartticket.api.vo.assignment.TicketAssignmentStatsVO;
import com.smartticket.api.vo.assignment.TicketGroupVO;
import com.smartticket.api.vo.assignment.TicketQueueMemberVO;
import com.smartticket.api.vo.assignment.TicketQueueVO;
import com.smartticket.api.vo.sla.TicketSlaInstanceVO;
import com.smartticket.api.vo.sla.TicketSlaPolicyVO;
import com.smartticket.api.vo.sla.TicketSlaScanResultVO;
import com.smartticket.biz.dto.assignment.TicketAssignmentPreviewDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentStatsDTO;
import com.smartticket.biz.dto.sla.TicketSlaScanResultDTO;
import com.smartticket.domain.entity.TicketApprovalTemplate;
import com.smartticket.domain.entity.TicketApprovalTemplateStep;
import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.entity.TicketQueueMember;
import com.smartticket.domain.entity.TicketSlaInstance;
import com.smartticket.domain.entity.TicketSlaPolicy;
import org.springframework.stereotype.Component;

@Component
public class P1ConfigAssembler {
    public TicketGroupVO toGroupVO(TicketGroup group) {
        if (group == null) {
            return null;
        }
        return TicketGroupVO.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .groupCode(group.getGroupCode())
                .ownerUserId(group.getOwnerUserId())
                .enabled(Integer.valueOf(1).equals(group.getEnabled()))
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    public TicketQueueVO toQueueVO(TicketQueue queue) {
        if (queue == null) {
            return null;
        }
        return TicketQueueVO.builder()
                .id(queue.getId())
                .queueName(queue.getQueueName())
                .queueCode(queue.getQueueCode())
                .groupId(queue.getGroupId())
                .enabled(Integer.valueOf(1).equals(queue.getEnabled()))
                .createdAt(queue.getCreatedAt())
                .updatedAt(queue.getUpdatedAt())
                .build();
    }

    public TicketQueueMemberVO toQueueMemberVO(TicketQueueMember member) {
        if (member == null) {
            return null;
        }
        return TicketQueueMemberVO.builder()
                .id(member.getId())
                .queueId(member.getQueueId())
                .userId(member.getUserId())
                .enabled(Integer.valueOf(1).equals(member.getEnabled()))
                .lastAssignedAt(member.getLastAssignedAt())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    public TicketSlaPolicyVO toSlaPolicyVO(TicketSlaPolicy policy) {
        if (policy == null) {
            return null;
        }
        return TicketSlaPolicyVO.builder()
                .id(policy.getId())
                .policyName(policy.getPolicyName())
                .category(policy.getCategory())
                .priority(policy.getPriority())
                .firstResponseMinutes(policy.getFirstResponseMinutes())
                .resolveMinutes(policy.getResolveMinutes())
                .enabled(Integer.valueOf(1).equals(policy.getEnabled()))
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    public TicketSlaInstanceVO toSlaInstanceVO(TicketSlaInstance instance) {
        if (instance == null) {
            return null;
        }
        return TicketSlaInstanceVO.builder()
                .id(instance.getId())
                .ticketId(instance.getTicketId())
                .policyId(instance.getPolicyId())
                .firstResponseDeadline(instance.getFirstResponseDeadline())
                .resolveDeadline(instance.getResolveDeadline())
                .breached(Integer.valueOf(1).equals(instance.getBreached()))
                .createdAt(instance.getCreatedAt())
                .updatedAt(instance.getUpdatedAt())
                .build();
    }

    public TicketSlaScanResultVO toSlaScanResultVO(TicketSlaScanResultDTO result) {
        if (result == null) {
            return null;
        }
        return TicketSlaScanResultVO.builder()
                .scanTime(result.getScanTime())
                .limit(result.getLimit())
                .candidateCount(result.getCandidateCount())
                .markedCount(result.getMarkedCount())
                .firstResponseBreachedCount(result.getFirstResponseBreachedCount())
                .resolveBreachedCount(result.getResolveBreachedCount())
                .escalatedCount(result.getEscalatedCount())
                .notifiedCount(result.getNotifiedCount())
                .breachedInstanceIds(result.getBreachedInstanceIds())
                .build();
    }

    public TicketAssignmentRuleVO toAssignmentRuleVO(TicketAssignmentRule rule) {
        if (rule == null) {
            return null;
        }
        return TicketAssignmentRuleVO.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .category(rule.getCategory())
                .priority(rule.getPriority())
                .targetGroupId(rule.getTargetGroupId())
                .targetQueueId(rule.getTargetQueueId())
                .targetUserId(rule.getTargetUserId())
                .weight(rule.getWeight())
                .enabled(Integer.valueOf(1).equals(rule.getEnabled()))
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    public TicketAssignmentPreviewVO toAssignmentPreviewVO(TicketAssignmentPreviewDTO preview) {
        if (preview == null) {
            return null;
        }
        return TicketAssignmentPreviewVO.builder()
                .ticketId(preview.getTicketId())
                .matched(preview.isMatched())
                .ruleId(preview.getRuleId())
                .ruleName(preview.getRuleName())
                .targetGroupId(preview.getTargetGroupId())
                .targetQueueId(preview.getTargetQueueId())
                .targetUserId(preview.getTargetUserId())
                .reason(preview.getReason())
                .build();
    }

    public TicketAssignmentStatsVO toAssignmentStatsVO(TicketAssignmentStatsDTO stats) {
        if (stats == null) {
            return null;
        }
        return TicketAssignmentStatsVO.builder()
                .autoAssignMatchedCount(stats.getAutoAssignMatchedCount())
                .autoAssignFallbackCount(stats.getAutoAssignFallbackCount())
                .autoAssignPendingCount(stats.getAutoAssignPendingCount())
                .claimedCount(stats.getClaimedCount())
                .totalAutoAssignCount(stats.getTotalAutoAssignCount())
                .autoAssignedCount(stats.getAutoAssignedCount())
                .autoAssignHitRate(stats.getAutoAssignHitRate())
                .build();
    }

    public TicketApprovalTemplateVO toApprovalTemplateVO(TicketApprovalTemplate template) {
        if (template == null) {
            return null;
        }
        return TicketApprovalTemplateVO.builder()
                .id(template.getId())
                .templateName(template.getTemplateName())
                .ticketType(template.getTicketType() == null ? null : template.getTicketType().getCode())
                .ticketTypeInfo(template.getTicketType() == null ? null : template.getTicketType().getInfo())
                .description(template.getDescription())
                .enabled(Integer.valueOf(1).equals(template.getEnabled()))
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .steps(template.getSteps() == null ? null : template.getSteps().stream().map(this::toApprovalTemplateStepVO).toList())
                .build();
    }

    public TicketApprovalTemplateStepVO toApprovalTemplateStepVO(TicketApprovalTemplateStep step) {
        if (step == null) {
            return null;
        }
        return TicketApprovalTemplateStepVO.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .stepName(step.getStepName())
                .approverId(step.getApproverId())
                .createdAt(step.getCreatedAt())
                .updatedAt(step.getUpdatedAt())
                .build();
    }
}
