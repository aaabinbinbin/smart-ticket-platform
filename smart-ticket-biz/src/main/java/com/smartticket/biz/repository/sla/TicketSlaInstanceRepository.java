package com.smartticket.biz.repository.sla;

import com.smartticket.domain.entity.TicketSlaInstance;
import com.smartticket.domain.mapper.TicketSlaInstanceMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * SLA 实例仓储封装，只负责数据访问。
 */
@Repository
public class TicketSlaInstanceRepository {
    private final TicketSlaInstanceMapper mapper;

    public TicketSlaInstanceRepository(TicketSlaInstanceMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(TicketSlaInstance instance) {
        return mapper.insert(instance);
    }

    public TicketSlaInstance findById(Long id) {
        return mapper.findById(id);
    }

    public TicketSlaInstance findByTicketId(Long ticketId) {
        return mapper.findByTicketId(ticketId);
    }

    public int updateByTicketId(TicketSlaInstance instance) {
        return mapper.updateByTicketId(instance);
    }

    public List<TicketSlaInstance> findBreachedCandidates(LocalDateTime now, int limit) {
        return mapper.findBreachedCandidates(now, limit);
    }

    public int markBreached(Long id) {
        return mapper.markBreached(id);
    }
}

