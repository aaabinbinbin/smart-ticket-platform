package com.smartticket.biz.service;

import com.smartticket.biz.dto.TicketQueueCommandDTO;
import com.smartticket.biz.dto.TicketQueuePageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketQueueRepository;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketQueue;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单队列业务服务。
 *
 * <p>队列既是 P1 的配置能力，也已经接入工单主流程的队列绑定和自动分派目标校验。
 * 当前仍未实现组内负载均衡、认领和无人可分配时的回退策略。</p>
 */
@Service
public class TicketQueueService {
    /** 工单队列仓储。 */
    private final TicketQueueRepository ticketQueueRepository;

    /** 工单组服务，用于校验队列所属组。 */
    private final TicketGroupService ticketGroupService;

    /** 工单权限服务，用于复用 ADMIN 判断。 */
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

    /** 创建工单队列。 */
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
     * 更新队列基础信息。
     *
     * <p>队列编码创建后不允许修改，避免后续规则引用失效。</p>
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

    /** 启用或停用工单队列。 */
    @Transactional
    public TicketQueue updateEnabled(CurrentUser operator, Long queueId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requireById(queueId);
        ticketQueueRepository.updateEnabled(queueId, enabled ? 1 : 0);
        return requireById(queueId);
    }

    /** 查询队列详情。 */
    public TicketQueue get(Long queueId) {
        return requireById(queueId);
    }

    /** 校验队列存在且启用，供 SLA 和自动分派等 P1 能力复用。 */
    public TicketQueue requireEnabled(Long queueId) {
        TicketQueue queue = requireById(queueId);
        if (!Integer.valueOf(1).equals(queue.getEnabled())) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_ASSIGNMENT_RULE, "目标队列已停用");
        }
        return queue;
    }

    /** 分页查询队列。 */
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

    /** 根据 ID 查询队列，不存在时抛出业务异常。 */
    private TicketQueue requireById(Long queueId) {
        TicketQueue queue = ticketQueueRepository.findById(queueId);
        if (queue == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_QUEUE_NOT_FOUND);
        }
        return queue;
    }

    /** 归一化配置编码。 */
    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    /** 归一化查询关键字。 */
    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
    }

    /** 将布尔值转换为数据库启停标记。 */
    private Integer toEnabled(Boolean enabled) {
        return enabled == null || enabled ? 1 : 0;
    }
}
