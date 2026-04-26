package com.smartticket.biz.repository.ticket;

import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.mapper.SysUserMapper;
import com.smartticket.domain.mapper.SysUserRoleMapper;
import com.smartticket.domain.mapper.TicketMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单仓储仓储接口。
 */
@Repository
public class TicketRepository {
    // 工单映射接口
    private final TicketMapper ticketMapper;
    // sys用户映射接口
    private final SysUserMapper sysUserMapper;
    // sys用户角色映射接口
    private final SysUserRoleMapper sysUserRoleMapper;

    /**
     * 构造工单仓储。
     */
    public TicketRepository(TicketMapper ticketMapper, SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper) {
        this.ticketMapper = ticketMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(Ticket ticket) { return ticketMapper.insert(ticket); }
    /**
     * 查询按ID。
     */
    public Ticket findById(Long id) { return ticketMapper.findById(id); }
    /**
     * 查询可见按ID。
     */
    public Ticket findVisibleById(Long id, Long userId) { return ticketMapper.findVisibleById(id, userId); }
    /**
     * 分页查询全部。
     */
    public List<Ticket> pageAll(String status, String type, String category, String priority, int offset, int limit) { return ticketMapper.pageAll(status, type, category, priority, offset, limit); }
    /**
     * 统计全部。
     */
    public long countAll(String status, String type, String category, String priority) { return ticketMapper.countAll(status, type, category, priority); }
    /**
     * 分页查询可见。
     */
    public List<Ticket> pageVisible(Long userId, String status, String type, String category, String priority, int offset, int limit) { return ticketMapper.pageVisible(userId, status, type, category, priority, offset, limit); }
    /**
     * 统计可见。
     */
    public long countVisible(Long userId, String status, String type, String category, String priority) { return ticketMapper.countVisible(userId, status, type, category, priority); }
    /**
     * 统计开放Assigned工单。
     */
    public long countOpenAssignedTickets(Long assigneeId) { return ticketMapper.countOpenAssignedTickets(assigneeId); }
    /**
     * 更新处理人。
     */
    public int updateAssignee(Long id, Long assigneeId, TicketStatusEnum expectedStatus) { return ticketMapper.updateAssignee(id, assigneeId, expectedStatus); }
    /**
     * 更新处理人并状态。
     */
    public int updateAssigneeAndStatus(Long id, Long assigneeId, TicketStatusEnum expectedStatus, TicketStatusEnum status) { return ticketMapper.updateAssigneeAndStatus(id, assigneeId, expectedStatus, status); }
    /**
     * 更新队列绑定关系。
     */
    public int updateQueueBinding(Long id, Long groupId, Long queueId) { return ticketMapper.updateQueueBinding(id, groupId, queueId); }
    /**
     * 更新状态。
     */
    public int updateStatus(Long id, TicketStatusEnum expectedStatus, TicketStatusEnum status, String solutionSummary) { return ticketMapper.updateStatus(id, expectedStatus, status, solutionSummary); }
    /**
     * 更新优先级。
     */
    public int updatePriority(Long id, TicketPriorityEnum priority) { return ticketMapper.updatePriority(id, priority); }
    /**
     * 查询用户按ID。
     */
    public SysUser findUserById(Long userId) { return sysUserMapper.findById(userId); }
    /**
     * 按角色编码查询用户。
     */
    public List<SysUser> findUsersByRoleCode(String roleCode) { return sysUserMapper.findByRoleCode(roleCode); }
    /**
     * 查询Roles按用户ID。
     */
    public List<SysRole> findRolesByUserId(Long userId) { return sysUserRoleMapper.findRolesByUserId(userId); }
}

