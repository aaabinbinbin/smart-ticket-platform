package com.smartticket.biz.repository.assignment;

import com.smartticket.domain.entity.TicketQueueMember;
import com.smartticket.domain.mapper.TicketQueueMemberMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单队列成员仓储仓储接口。
 */
@Repository
public class TicketQueueMemberRepository {
    // 工单队列成员映射接口
    private final TicketQueueMemberMapper ticketQueueMemberMapper;

    /**
     * 构造工单队列成员仓储。
     */
    public TicketQueueMemberRepository(TicketQueueMemberMapper ticketQueueMemberMapper) {
        this.ticketQueueMemberMapper = ticketQueueMemberMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketQueueMember member) {
        return ticketQueueMemberMapper.insert(member);
    }

    /**
     * 查询按ID。
     */
    public TicketQueueMember findById(Long id) {
        return ticketQueueMemberMapper.findById(id);
    }

    /**
     * 查询按队列ID并用户ID。
     */
    public TicketQueueMember findByQueueIdAndUserId(Long queueId, Long userId) {
        return ticketQueueMemberMapper.findByQueueIdAndUserId(queueId, userId);
    }

    /**
     * 查询按队列ID。
     */
    public List<TicketQueueMember> findByQueueId(Long queueId, Integer enabled) {
        return ticketQueueMemberMapper.findByQueueId(queueId, enabled);
    }

    /**
     * 更新启用。
     */
    public int updateEnabled(Long id, Integer enabled) {
        return ticketQueueMemberMapper.updateEnabled(id, enabled);
    }

    /**
     * 更新最近分派时间。
     */
    public int updateLastAssignedAt(Long id) {
        return ticketQueueMemberMapper.updateLastAssignedAt(id);
    }
}

