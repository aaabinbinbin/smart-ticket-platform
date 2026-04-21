package com.smartticket.biz.repository;

import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.mapper.TicketQueueMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单队列仓储封装，只负责数据访问。
 */
@Repository
public class TicketQueueRepository {
    private final TicketQueueMapper ticketQueueMapper;

    public TicketQueueRepository(TicketQueueMapper ticketQueueMapper) {
        this.ticketQueueMapper = ticketQueueMapper;
    }

    public int insert(TicketQueue ticketQueue) {
        return ticketQueueMapper.insert(ticketQueue);
    }

    public TicketQueue findById(Long id) {
        return ticketQueueMapper.findById(id);
    }

    public TicketQueue findByCode(String queueCode) {
        return ticketQueueMapper.findByCode(queueCode);
    }

    public List<TicketQueue> page(Long groupId, String keyword, Integer enabled, int offset, int limit) {
        return ticketQueueMapper.page(groupId, keyword, enabled, offset, limit);
    }

    public long count(Long groupId, String keyword, Integer enabled) {
        return ticketQueueMapper.count(groupId, keyword, enabled);
    }

    public int update(TicketQueue ticketQueue) {
        return ticketQueueMapper.update(ticketQueue);
    }

    public int updateEnabled(Long id, Integer enabled) {
        return ticketQueueMapper.updateEnabled(id, enabled);
    }
}
