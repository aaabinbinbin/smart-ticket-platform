package com.smartticket.biz.service.assignment;

import com.smartticket.biz.dto.assignment.TicketQueueMemberCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.assignment.TicketQueueMemberRepository;
import com.smartticket.biz.repository.assignment.TicketQueueRepository;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.entity.TicketQueueMember;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketQueueMemberService {
    private final TicketQueueMemberRepository ticketQueueMemberRepository;
    private final TicketQueueRepository ticketQueueRepository;
    private final TicketRepository ticketRepository;
    private final TicketPermissionService permissionService;

    public TicketQueueMemberService(
            TicketQueueMemberRepository ticketQueueMemberRepository,
            TicketQueueRepository ticketQueueRepository,
            TicketRepository ticketRepository,
            TicketPermissionService permissionService
    ) {
        this.ticketQueueMemberRepository = ticketQueueMemberRepository;
        this.ticketQueueRepository = ticketQueueRepository;
        this.ticketRepository = ticketRepository;
        this.permissionService = permissionService;
    }

    @Transactional
    public TicketQueueMember create(CurrentUser operator, Long queueId, TicketQueueMemberCommandDTO command) {
        permissionService.requireAdmin(operator);
        requireEnabledQueue(queueId);
        requireStaffUser(command.getUserId());
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

    public List<TicketQueueMember> list(Long queueId, Boolean enabled) {
        requireEnabledQueue(queueId);
        return ticketQueueMemberRepository.findByQueueId(queueId, enabled == null ? null : toEnabled(enabled));
    }

    public List<TicketQueueMember> listEnabledMembers(Long queueId) {
        return ticketQueueMemberRepository.findByQueueId(queueId, 1);
    }

    public boolean isEnabledMember(Long queueId, Long userId) {
        if (queueId == null || userId == null) {
            return false;
        }
        TicketQueueMember member = ticketQueueMemberRepository.findByQueueIdAndUserId(queueId, userId);
        return member != null && Integer.valueOf(1).equals(member.getEnabled());
    }

    public void markAssigned(Long memberId) {
        ticketQueueMemberRepository.updateLastAssignedAt(memberId);
    }

    private TicketQueueMember requireById(Long memberId) {
        TicketQueueMember member = ticketQueueMemberRepository.findById(memberId);
        if (member == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_QUEUE_NOT_FOUND);
        }
        return member;
    }

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

    private void requireStaffUser(Long userId) {
        SysUser user = ticketRepository.findUserById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(BusinessErrorCode.ASSIGNEE_NOT_FOUND);
        }
        boolean isStaff = ticketRepository.findRolesByUserId(userId)
                .stream()
                .map(SysRole::getRoleCode)
                .anyMatch("STAFF"::equals);
        if (!isStaff) {
            throw new BusinessException(BusinessErrorCode.ASSIGNEE_NOT_STAFF);
        }
    }

    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }
}

