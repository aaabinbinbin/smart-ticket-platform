package com.smartticket.biz.repository;

import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.mapper.SysUserMapper;
import com.smartticket.domain.mapper.SysUserRoleMapper;
import com.smartticket.domain.mapper.TicketMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单仓储封装。
 *
 * <p>这里只封装数据访问，不放状态流转、权限判断等业务规则。</p>
 */
@Repository
public class TicketRepository {
    private final TicketMapper ticketMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public TicketRepository(
            TicketMapper ticketMapper,
            SysUserMapper sysUserMapper,
            SysUserRoleMapper sysUserRoleMapper
    ) {
        this.ticketMapper = ticketMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    public int insert(Ticket ticket) {
        return ticketMapper.insert(ticket);
    }

    public Ticket findById(Long id) {
        return ticketMapper.findById(id);
    }

    public Ticket findVisibleById(Long id, Long userId) {
        return ticketMapper.findVisibleById(id, userId);
    }

    public List<Ticket> pageAll(String status, String category, String priority, int offset, int limit) {
        return ticketMapper.pageAll(status, category, priority, offset, limit);
    }

    public long countAll(String status, String category, String priority) {
        return ticketMapper.countAll(status, category, priority);
    }

    public List<Ticket> pageVisible(Long userId, String status, String category, String priority, int offset, int limit) {
        return ticketMapper.pageVisible(userId, status, category, priority, offset, limit);
    }

    public long countVisible(Long userId, String status, String category, String priority) {
        return ticketMapper.countVisible(userId, status, category, priority);
    }

    public int updateAssignee(Long id, Long assigneeId, TicketStatusEnum expectedStatus) {
        return ticketMapper.updateAssignee(id, assigneeId, expectedStatus);
    }

    public int updateAssigneeAndStatus(
            Long id,
            Long assigneeId,
            TicketStatusEnum expectedStatus,
            TicketStatusEnum status
    ) {
        return ticketMapper.updateAssigneeAndStatus(id, assigneeId, expectedStatus, status);
    }

    public int updateQueueBinding(Long id, Long groupId, Long queueId) {
        return ticketMapper.updateQueueBinding(id, groupId, queueId);
    }

    public int updateStatus(
            Long id,
            TicketStatusEnum expectedStatus,
            TicketStatusEnum status,
            String solutionSummary
    ) {
        return ticketMapper.updateStatus(id, expectedStatus, status, solutionSummary);
    }

    public SysUser findUserById(Long userId) {
        return sysUserMapper.findById(userId);
    }

    public List<SysRole> findRolesByUserId(Long userId) {
        return sysUserRoleMapper.findRolesByUserId(userId);
    }
}
