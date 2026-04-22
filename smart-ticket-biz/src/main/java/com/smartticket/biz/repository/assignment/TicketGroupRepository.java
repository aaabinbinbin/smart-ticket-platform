package com.smartticket.biz.repository.assignment;

import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.mapper.TicketGroupMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单组仓储封装，只负责数据访问。
 */
@Repository
public class TicketGroupRepository {
    private final TicketGroupMapper ticketGroupMapper;

    public TicketGroupRepository(TicketGroupMapper ticketGroupMapper) {
        this.ticketGroupMapper = ticketGroupMapper;
    }

    public int insert(TicketGroup ticketGroup) {
        return ticketGroupMapper.insert(ticketGroup);
    }

    public TicketGroup findById(Long id) {
        return ticketGroupMapper.findById(id);
    }

    public TicketGroup findByCode(String groupCode) {
        return ticketGroupMapper.findByCode(groupCode);
    }

    public List<TicketGroup> page(String keyword, Integer enabled, int offset, int limit) {
        return ticketGroupMapper.page(keyword, enabled, offset, limit);
    }

    public long count(String keyword, Integer enabled) {
        return ticketGroupMapper.count(keyword, enabled);
    }

    public int update(TicketGroup ticketGroup) {
        return ticketGroupMapper.update(ticketGroup);
    }

    public int updateEnabled(Long id, Integer enabled) {
        return ticketGroupMapper.updateEnabled(id, enabled);
    }
}

