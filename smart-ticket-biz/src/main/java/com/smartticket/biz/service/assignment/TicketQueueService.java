package com.smartticket.biz.service.assignment;

import com.smartticket.biz.dto.assignment.TicketQueueCommandDTO;
import com.smartticket.biz.dto.assignment.TicketQueuePageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.assignment.TicketQueueRepository;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketQueue;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketQueueService {
    private final TicketQueueRepository ticketQueueRepository;
    private final TicketGroupService ticketGroupService;
    private final TicketPermissionService permissionService;

    public TicketQueueService(
            TicketQueueRepository ticketQueueRepository,
            TicketGroupService ticketGroupService,
            TicketPermissionService permissionService
    ) {
        this.ticketQueueRepository = ticketQueueRepository;
        this.ticketGroupService = ticketGroupService;
        this.permissionService = permissionService;
    }

    @Transactional
    public TicketQueue create(CurrentUser operator, TicketQueueCommandDTO command) {
        permissionService.requireAdmin(operator);
        ticketGroupService.requireEnabled(command.getGroupId());
        String queueCode = normalizeCode(command.getQueueCode());
        if (ticketQueueRepository.findByCode(queueCode) != null) {
            throw new BusinessException(BusinessErrorCode.TICKET_QUEUE_CODE_DUPLICATED, queueCode);
        }
        TicketQueue queue = TicketQueue.builder()
                .queueName(command.getQueueName())
                .queueCode(queueCode)
                .groupId(command.getGroupId())
                .enabled(toEnabled(command.getEnabled()))
                .build();
        ticketQueueRepository.insert(queue);
        return requireById(queue.getId());
    }

    @Transactional
    public TicketQueue update(CurrentUser operator, Long queueId, TicketQueueCommandDTO command) {
        permissionService.requireAdmin(operator);
        TicketQueue existing = requireById(queueId);
        ticketGroupService.requireEnabled(command.getGroupId());
        existing.setQueueName(command.getQueueName());
        existing.setGroupId(command.getGroupId());
        existing.setEnabled(toEnabled(command.getEnabled()));
        ticketQueueRepository.update(existing);
        return requireById(queueId);
    }

    @Transactional
    public TicketQueue updateEnabled(CurrentUser operator, Long queueId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requireById(queueId);
        ticketQueueRepository.updateEnabled(queueId, enabled ? 1 : 0);
        return requireById(queueId);
    }

    public TicketQueue get(Long queueId) {
        return requireById(queueId);
    }

    public TicketQueue requireEnabled(Long queueId) {
        TicketQueue queue = requireById(queueId);
        if (!Integer.valueOf(1).equals(queue.getEnabled())) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "目标队列已停用");
        }
        return queue;
    }

    public PageResult<TicketQueue> page(TicketQueuePageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        int offset = (pageNo - 1) * pageSize;
        String keyword = normalizeKeyword(query.getKeyword());
        Integer enabled = query.getEnabled() == null ? null : toEnabled(query.getEnabled());
        List<TicketQueue> records = ticketQueueRepository.page(query.getGroupId(), keyword, enabled, offset, pageSize);
        long total = ticketQueueRepository.count(query.getGroupId(), keyword, enabled);
        return PageResult.<TicketQueue>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    private TicketQueue requireById(Long queueId) {
        TicketQueue queue = ticketQueueRepository.findById(queueId);
        if (queue == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_QUEUE_NOT_FOUND);
        }
        return queue;
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
    }

    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }
}

