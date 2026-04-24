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

/**
 * 工单队列服务。
 */
@Service
public class TicketQueueService {
    // 工单队列仓储
    private final TicketQueueRepository ticketQueueRepository;
    // 工单分组服务
    private final TicketGroupService ticketGroupService;
    // 权限服务
    private final TicketPermissionService permissionService;

    /**
     * 构造工单队列服务。
     */
    public TicketQueueService(
            TicketQueueRepository ticketQueueRepository,
            TicketGroupService ticketGroupService,
            TicketPermissionService permissionService
    ) {
        this.ticketQueueRepository = ticketQueueRepository;
        this.ticketGroupService = ticketGroupService;
        this.permissionService = permissionService;
    }

    /**
     * 创建。
     */
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

    /**
     * 更新。
     */
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

    /**
     * 更新启用。
     */
    @Transactional
    public TicketQueue updateEnabled(CurrentUser operator, Long queueId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requireById(queueId);
        ticketQueueRepository.updateEnabled(queueId, enabled ? 1 : 0);
        return requireById(queueId);
    }

    /**
     * 获取详情。
     */
    public TicketQueue get(Long queueId) {
        return requireById(queueId);
    }

    /**
     * 校验启用。
     */
    public TicketQueue requireEnabled(Long queueId) {
        TicketQueue queue = requireById(queueId);
        if (!Integer.valueOf(1).equals(queue.getEnabled())) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "目标队列已停用");
        }
        return queue;
    }

    /**
     * 分页查询。
     */
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

    /**
     * 校验按ID。
     */
    private TicketQueue requireById(Long queueId) {
        TicketQueue queue = ticketQueueRepository.findById(queueId);
        if (queue == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_QUEUE_NOT_FOUND);
        }
        return queue;
    }

    /**
     * 规范化编码。
     */
    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    /**
     * 规范化关键字。
     */
    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
    }

    /**
     * 转换为启用。
     */
    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }
}

