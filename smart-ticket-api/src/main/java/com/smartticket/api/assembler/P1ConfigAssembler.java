package com.smartticket.api.assembler;

import com.smartticket.api.vo.p1.TicketAssignmentPreviewVO;
import com.smartticket.api.vo.p1.TicketAssignmentRuleVO;
import com.smartticket.api.vo.p1.TicketGroupVO;
import com.smartticket.api.vo.p1.TicketQueueVO;
import com.smartticket.api.vo.p1.TicketSlaInstanceVO;
import com.smartticket.api.vo.p1.TicketSlaPolicyVO;
import com.smartticket.api.vo.p1.TicketSlaScanResultVO;
import com.smartticket.biz.dto.TicketAssignmentPreviewDTO;
import com.smartticket.biz.dto.TicketSlaScanResultDTO;
import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.entity.TicketSlaInstance;
import com.smartticket.domain.entity.TicketSlaPolicy;
import org.springframework.stereotype.Component;

/**
 * P1 配置对象响应组装器。
 */
@Component
public class P1ConfigAssembler {

    /** 将工单组实体转换为响应视图。 */
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

    /** 将工单队列实体转换为响应视图。 */
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

    /** 将 SLA 策略实体转换为响应视图。 */
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

    /** 将 SLA 实例实体转换为响应视图。 */
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

    /** 将自动分派规则实体转换为响应视图。 */
    /** 将 SLA 违约扫描结果转换为响应视图。 */
    public TicketSlaScanResultVO toSlaScanResultVO(TicketSlaScanResultDTO result) {
        if (result == null) {
            return null;
        }
        return TicketSlaScanResultVO.builder()
                .scanTime(result.getScanTime())
                .limit(result.getLimit())
                .candidateCount(result.getCandidateCount())
                .markedCount(result.getMarkedCount())
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

    /** 将自动分派 preview 结果转换为响应视图。 */
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
}
