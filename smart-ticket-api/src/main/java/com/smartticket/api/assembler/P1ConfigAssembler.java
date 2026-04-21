package com.smartticket.api.assembler;

import com.smartticket.api.vo.p1.TicketAssignmentPreviewVO;
import com.smartticket.api.vo.p1.TicketAssignmentRuleVO;
import com.smartticket.api.vo.p1.TicketAssignmentStatsVO;
import com.smartticket.api.vo.p1.TicketGroupVO;
import com.smartticket.api.vo.p1.TicketQueueVO;
import com.smartticket.api.vo.p1.TicketQueueMemberVO;
import com.smartticket.api.vo.p1.TicketSlaInstanceVO;
import com.smartticket.api.vo.p1.TicketSlaPolicyVO;
import com.smartticket.api.vo.p1.TicketSlaScanResultVO;
import com.smartticket.biz.dto.TicketAssignmentPreviewDTO;
import com.smartticket.biz.dto.TicketAssignmentStatsDTO;
import com.smartticket.biz.dto.TicketSlaScanResultDTO;
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
        return TicketGroupVO.builder().id(group.getId()).groupName(group.getGroupName()).groupCode(group.getGroupCode()).ownerUserId(group.getOwnerUserId()).enabled(Integer.valueOf(1).equals(group.getEnabled())).createdAt(group.getCreatedAt()).updatedAt(group.getUpdatedAt()).build();
    }

    public TicketQueueVO toQueueVO(TicketQueue queue) {
        if (queue == null) {
            return null;
        }
        return TicketQueueVO.builder().id(queue.getId()).queueName(queue.getQueueName()).queueCode(queue.getQueueCode()).groupId(queue.getGroupId()).enabled(Integer.valueOf(1).equals(queue.getEnabled())).createdAt(queue.getCreatedAt()).updatedAt(queue.getUpdatedAt()).build();
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
        return TicketSlaPolicyVO.builder().id(policy.getId()).policyName(policy.getPolicyName()).category(policy.getCategory()).priority(policy.getPriority()).firstResponseMinutes(policy.getFirstResponseMinutes()).resolveMinutes(policy.getResolveMinutes()).enabled(Integer.valueOf(1).equals(policy.getEnabled())).createdAt(policy.getCreatedAt()).updatedAt(policy.getUpdatedAt()).build();
    }

    public TicketSlaInstanceVO toSlaInstanceVO(TicketSlaInstance instance) {
        if (instance == null) {
            return null;
        }
        return TicketSlaInstanceVO.builder().id(instance.getId()).ticketId(instance.getTicketId()).policyId(instance.getPolicyId()).firstResponseDeadline(instance.getFirstResponseDeadline()).resolveDeadline(instance.getResolveDeadline()).breached(Integer.valueOf(1).equals(instance.getBreached())).createdAt(instance.getCreatedAt()).updatedAt(instance.getUpdatedAt()).build();
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
        return TicketAssignmentRuleVO.builder().id(rule.getId()).ruleName(rule.getRuleName()).category(rule.getCategory()).priority(rule.getPriority()).targetGroupId(rule.getTargetGroupId()).targetQueueId(rule.getTargetQueueId()).targetUserId(rule.getTargetUserId()).weight(rule.getWeight()).enabled(Integer.valueOf(1).equals(rule.getEnabled())).createdAt(rule.getCreatedAt()).updatedAt(rule.getUpdatedAt()).build();
    }

    public TicketAssignmentPreviewVO toAssignmentPreviewVO(TicketAssignmentPreviewDTO preview) {
        if (preview == null) {
            return null;
        }
        return TicketAssignmentPreviewVO.builder().ticketId(preview.getTicketId()).matched(preview.isMatched()).ruleId(preview.getRuleId()).ruleName(preview.getRuleName()).targetGroupId(preview.getTargetGroupId()).targetQueueId(preview.getTargetQueueId()).targetUserId(preview.getTargetUserId()).reason(preview.getReason()).build();
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
}
