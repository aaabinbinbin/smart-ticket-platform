package com.smartticket.biz.service.assignment;

import com.smartticket.biz.dto.assignment.TicketGroupCommandDTO;
import com.smartticket.biz.dto.assignment.TicketGroupPageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.assignment.TicketGroupRepository;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketGroup;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单组业务服务。
 *
 * <p>工单组是 P1 队列、SLA 和自动分派的基础配置。当前阶段只提供管理能力，
 * 不改变工单主流程。</p>
 */
@Service
public class TicketGroupService {
    /** 工单组仓储。 */
    private final TicketGroupRepository ticketGroupRepository;

    /** 工单权限服务，用于复用 ADMIN 判断。 */
    private final TicketPermissionService permissionService;

    /**
     * 构造工单分组服务。
     */
    public TicketGroupService(
            TicketGroupRepository ticketGroupRepository,
            TicketPermissionService permissionService
    ) {
        this.ticketGroupRepository = ticketGroupRepository;
        this.permissionService = permissionService;
    }

    /**
     * 创建工单组。
     *
     * @param operator 当前操作人，必须是 ADMIN
     * @param command 创建命令
     * @return 创建后的工单组
     */
    @Transactional
    public TicketGroup create(CurrentUser operator, TicketGroupCommandDTO command) {
        permissionService.requireAdmin(operator);
        String groupCode = normalizeCode(command.getGroupCode());
        if (ticketGroupRepository.findByCode(groupCode) != null) {
            throw new BusinessException(BusinessErrorCode.TICKET_GROUP_CODE_DUPLICATED, groupCode);
        }
        TicketGroup ticketGroup = TicketGroup.builder()
                .groupName(command.getGroupName())
                .groupCode(groupCode)
                .ownerUserId(command.getOwnerUserId())
                .enabled(toEnabled(command.getEnabled()))
                .build();
        ticketGroupRepository.insert(ticketGroup);
        return requireById(ticketGroup.getId());
    }

    /**
     * 更新工单组基础信息。
     *
     * <p>工单组编码创建后不允许修改，避免后续规则引用失效。</p>
     */
    @Transactional
    public TicketGroup update(CurrentUser operator, Long groupId, TicketGroupCommandDTO command) {
        permissionService.requireAdmin(operator);
        TicketGroup existing = requireById(groupId);
        existing.setGroupName(command.getGroupName());
        existing.setOwnerUserId(command.getOwnerUserId());
        existing.setEnabled(toEnabled(command.getEnabled()));
        ticketGroupRepository.update(existing);
        return requireById(groupId);
    }

    /** 启用或停用工单组。 */
    @Transactional
    public TicketGroup updateEnabled(CurrentUser operator, Long groupId, boolean enabled) {
        permissionService.requireAdmin(operator);
        requireById(groupId);
        ticketGroupRepository.updateEnabled(groupId, enabled ? 1 : 0);
        return requireById(groupId);
    }

    /** 查询工单组详情。 */
    public TicketGroup get(Long groupId) {
        return requireById(groupId);
    }

    /** 分页查询工单组。 */
    public PageResult<TicketGroup> page(TicketGroupPageQueryDTO query) {
        int pageNo = Math.max(query.getPageNo(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), 100);
        int offset = (pageNo - 1) * pageSize;
        String keyword = normalizeKeyword(query.getKeyword());
        Integer enabled = query.getEnabled() == null ? null : toEnabled(query.getEnabled());
        List<TicketGroup> records = ticketGroupRepository.page(keyword, enabled, offset, pageSize);
        long total = ticketGroupRepository.count(keyword, enabled);
        return PageResult.<TicketGroup>builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .total(total)
                .records(records)
                .build();
    }

    /** 校验工单组存在且启用，供队列、SLA 和分派规则复用。 */
    public TicketGroup requireEnabled(Long groupId) {
        TicketGroup group = requireById(groupId);
        if (!Integer.valueOf(1).equals(group.getEnabled())) {
            throw new BusinessException(BusinessErrorCode.TICKET_GROUP_DISABLED);
        }
        return group;
    }

    /** 根据 ID 查询工单组，不存在时抛出业务异常。 */
    private TicketGroup requireById(Long groupId) {
        TicketGroup ticketGroup = ticketGroupRepository.findById(groupId);
        if (ticketGroup == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_GROUP_NOT_FOUND);
        }
        return ticketGroup;
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

