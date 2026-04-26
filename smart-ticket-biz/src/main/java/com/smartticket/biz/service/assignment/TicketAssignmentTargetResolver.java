package com.smartticket.biz.service.assignment;

import com.smartticket.biz.repository.assignment.TicketQueueRepository;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.ticket.TicketUserDirectoryService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.entity.TicketQueueMember;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 自动分派目标解析器。
 *
 * <p>专门负责把命中的分派规则解析成最终的组、队列和处理人。
 * 候选队列选择、最小负载分配以及组长兜底都集中在这里。</p>
 */
@Component
public class TicketAssignmentTargetResolver {
    // 工单分组服务
    private final TicketGroupService ticketGroupService;
    // 工单队列服务
    private final TicketQueueService ticketQueueService;
    // 工单队列仓储
    private final TicketQueueRepository ticketQueueRepository;
    // 工单队列成员服务
    private final TicketQueueMemberService ticketQueueMemberService;
    // 工单仓储
    private final TicketRepository ticketRepository;
    // 工单用户目录服务
    private final TicketUserDirectoryService ticketUserDirectoryService;

    /**
     * 构造工单分派目标解析器。
     */
    public TicketAssignmentTargetResolver(
            TicketGroupService ticketGroupService,
            TicketQueueService ticketQueueService,
            TicketQueueRepository ticketQueueRepository,
            TicketQueueMemberService ticketQueueMemberService,
            TicketRepository ticketRepository,
            TicketUserDirectoryService ticketUserDirectoryService
    ) {
        this.ticketGroupService = ticketGroupService;
        this.ticketQueueService = ticketQueueService;
        this.ticketQueueRepository = ticketQueueRepository;
        this.ticketQueueMemberService = ticketQueueMemberService;
        this.ticketRepository = ticketRepository;
        this.ticketUserDirectoryService = ticketUserDirectoryService;
    }

    /**
     * 处理解析。
     */
    public AssignmentTarget resolve(TicketAssignmentRule rule) {
        if (rule.getTargetUserId() != null) {
            return new AssignmentTarget(rule.getTargetGroupId(), rule.getTargetQueueId(), rule.getTargetUserId(), null, false);
        }

        Long groupId = rule.getTargetGroupId();
        Long queueId = rule.getTargetQueueId();
        if (queueId != null && groupId == null) {
            groupId = ticketQueueService.get(queueId).getGroupId();
        }

        List<TicketQueue> candidateQueues = resolveCandidateQueues(groupId, queueId);
        QueueCandidate candidate = selectLeastLoadedCandidate(candidateQueues);
        if (candidate != null) {
            return new AssignmentTarget(candidate.groupId(), candidate.queueId(), candidate.assigneeId(), candidate.memberId(), false);
        }

        Long fallbackAssigneeId = resolveGroupOwnerFallback(groupId);
        Long fallbackQueueId = queueId != null ? queueId : candidateQueues.stream().map(TicketQueue::getId).findFirst().orElse(null);
        return new AssignmentTarget(groupId, fallbackQueueId, fallbackAssigneeId, null, fallbackAssigneeId != null);
    }

    /**
     * 解析候选Queues。
     */
    private List<TicketQueue> resolveCandidateQueues(Long groupId, Long queueId) {
        if (queueId != null) {
            return List.of(ticketQueueService.requireEnabled(queueId));
        }
        if (groupId == null) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "自动分派至少要配置目标组、队列或处理人");
        }
        ticketGroupService.requireEnabled(groupId);
        return ticketQueueRepository.findEnabledByGroupId(groupId);
    }

    /**
     * 处理最空闲候选人。
     */
    private QueueCandidate selectLeastLoadedCandidate(List<TicketQueue> candidateQueues) {
        List<QueueCandidate> candidates = new ArrayList<>();
        for (TicketQueue queue : candidateQueues) {
            for (TicketQueueMember member : ticketQueueMemberService.listEnabledMembers(queue.getId())) {
                candidates.add(new QueueCandidate(
                        queue.getGroupId(),
                        queue.getId(),
                        member.getId(),
                        member.getUserId(),
                        ticketRepository.countOpenAssignedTickets(member.getUserId()),
                        member.getLastAssignedAt()
                ));
            }
        }
        return candidates.stream()
                .min(Comparator.comparingLong(QueueCandidate::load)
                        .thenComparing(QueueCandidate::lastAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(QueueCandidate::memberId))
                .orElse(null);
    }

    /**
     * 解析分组OwnerFallback。
     */
    private Long resolveGroupOwnerFallback(Long groupId) {
        if (groupId == null) {
            return null;
        }
        TicketGroup group = ticketGroupService.requireEnabled(groupId);
        if (group.getOwnerUserId() == null) {
            return null;
        }
        return ticketUserDirectoryService.isStaffUser(group.getOwnerUserId()) ? group.getOwnerUserId() : null;
    }

    public record AssignmentTarget(Long groupId, Long queueId, Long assigneeId, Long memberId, boolean fallback) {
    }

    private record QueueCandidate(
            Long groupId,
            Long queueId,
            Long memberId,
            Long assigneeId,
            long load,
            LocalDateTime lastAssignedAt
    ) {
    }
}
