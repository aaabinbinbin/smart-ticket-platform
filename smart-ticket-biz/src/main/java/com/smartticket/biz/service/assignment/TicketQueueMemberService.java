package com.smartticket.biz.service.assignment;

import com.smartticket.biz.dto.assignment.TicketQueueMemberCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.assignment.TicketQueueMemberRepository;
import com.smartticket.biz.repository.assignment.TicketQueueRepository;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.biz.service.ticket.TicketUserDirectoryService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.entity.TicketQueueMember;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单队列成员服务。
 */
@Service
public class TicketQueueMemberService {
    // 工单队列成员仓储
    private final TicketQueueMemberRepository ticketQueueMemberRepository;
    // 工单队列仓储
    private final TicketQueueRepository ticketQueueRepository;
    // 权限服务
    private final TicketPermissionService permissionService;
    // 工单用户目录服务
    private final TicketUserDirectoryService ticketUserDirectoryService;

    /**
     * 构造工单队列成员服务。
     */
    public TicketQueueMemberService(
            TicketQueueMemberRepository ticketQueueMemberRepository,
            TicketQueueRepository ticketQueueRepository,
            TicketPermissionService permissionService,
            TicketUserDirectoryService ticketUserDirectoryService
    ) {
        this.ticketQueueMemberRepository = ticketQueueMemberRepository;
        this.ticketQueueRepository = ticketQueueRepository;
        this.permissionService = permissionService;
        this.ticketUserDirectoryService = ticketUserDirectoryService;
    }

    /**
     * 创建。
     */
    @Transactional
    public TicketQueueMember create(CurrentUser operator, Long queueId, TicketQueueMemberCommandDTO command) {
        permissionService.requireAdmin(operator);
        requireEnabledQueue(queueId);
        ticketUserDirectoryService.requireStaffUser(command.getUserId());
        TicketQueueMember existing = ticketQueueMemberRepository.findByQueueIdAndUserId(queueId, command.getUserId());
        if (existing != null) {
            ticketQueueMemberRepository.updateEnabled(existing.getId(), toEnabled(command.getEnabled()));
            return requireById(existing.getId());
        }
        TicketQueueMember member = TicketQueueMember.builder()
                .queueId(queueId)
                .userId(command.getUserId())
                .enabled(toEnabled(command.getEnabled()))
                .build();
        ticketQueueMemberRepository.insert(member);
        return requireById(member.getId());
    }

    /**
     * 更新启用。
     */
    @Transactional
    public TicketQueueMember updateEnabled(CurrentUser operator, Long queueId, Long memberId, boolean enabled) {
        permissionService.requireAdmin(operator);
        TicketQueueMember member = requireById(memberId);
        if (!queueId.equals(member.getQueueId())) {
            throw new BusinessException(BusinessErrorCode.TICKET_QUEUE_NOT_FOUND);
        }
        ticketQueueMemberRepository.updateEnabled(memberId, enabled ? 1 : 0);
        return requireById(memberId);
    }

    /**
     * 处理列表。
     */
    public List<TicketQueueMember> list(Long queueId, Boolean enabled) {
        requireEnabledQueue(queueId);
        return ticketQueueMemberRepository.findByQueueId(queueId, enabled == null ? null : toEnabled(enabled));
    }

    /**
     * 查询启用成员。
     */
    public List<TicketQueueMember> listEnabledMembers(Long queueId) {
        return ticketQueueMemberRepository.findByQueueId(queueId, 1);
    }

    /**
     * 处理启用成员。
     */
    public boolean isEnabledMember(Long queueId, Long userId) {
        if (queueId == null || userId == null) {
            return false;
        }
        TicketQueueMember member = ticketQueueMemberRepository.findByQueueIdAndUserId(queueId, userId);
        return member != null && Integer.valueOf(1).equals(member.getEnabled());
    }

    /**
     * 处理Assigned。
     */
    public void markAssigned(Long memberId) {
        ticketQueueMemberRepository.updateLastAssignedAt(memberId);
    }

    /**
     * 校验按ID。
     */
    private TicketQueueMember requireById(Long memberId) {
        TicketQueueMember member = ticketQueueMemberRepository.findById(memberId);
        if (member == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_QUEUE_NOT_FOUND);
        }
        return member;
    }

    /**
     * 校验启用队列。
     */
    private TicketQueue requireEnabledQueue(Long queueId) {
        TicketQueue queue = ticketQueueRepository.findById(queueId);
        if (queue == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_QUEUE_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(queue.getEnabled())) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "目标队列已停用");
        }
        return queue;
    }

    /**
     * 转换为启用。
     */
    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }
}

