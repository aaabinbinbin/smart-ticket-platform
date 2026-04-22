package com.smartticket.biz.repository.assignment;

import com.smartticket.domain.entity.TicketQueueMember;
import com.smartticket.domain.mapper.TicketQueueMemberMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TicketQueueMemberRepository {
    private final TicketQueueMemberMapper ticketQueueMemberMapper;

    public TicketQueueMemberRepository(TicketQueueMemberMapper ticketQueueMemberMapper) {
        this.ticketQueueMemberMapper = ticketQueueMemberMapper;
    }

    public int insert(TicketQueueMember member) {
        return ticketQueueMemberMapper.insert(member);
    }

    public TicketQueueMember findById(Long id) {
        return ticketQueueMemberMapper.findById(id);
    }

    public TicketQueueMember findByQueueIdAndUserId(Long queueId, Long userId) {
        return ticketQueueMemberMapper.findByQueueIdAndUserId(queueId, userId);
    }

    public List<TicketQueueMember> findByQueueId(Long queueId, Integer enabled) {
        return ticketQueueMemberMapper.findByQueueId(queueId, enabled);
    }

    public int updateEnabled(Long id, Integer enabled) {
        return ticketQueueMemberMapper.updateEnabled(id, enabled);
    }

    public int updateLastAssignedAt(Long id) {
        return ticketQueueMemberMapper.updateLastAssignedAt(id);
    }
}

