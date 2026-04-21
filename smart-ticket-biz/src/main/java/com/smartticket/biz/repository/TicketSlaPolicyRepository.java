package com.smartticket.biz.repository;

import com.smartticket.domain.entity.TicketSlaPolicy;
import com.smartticket.domain.mapper.TicketSlaPolicyMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * SLA 策略仓储封装，只负责数据访问。
 */
@Repository
public class TicketSlaPolicyRepository {
    private final TicketSlaPolicyMapper mapper;

    public TicketSlaPolicyRepository(TicketSlaPolicyMapper mapper) {
        this.mapper = mapper;
    }

    public int insert(TicketSlaPolicy policy) {
        return mapper.insert(policy);
    }

    public TicketSlaPolicy findById(Long id) {
        return mapper.findById(id);
    }

    public List<TicketSlaPolicy> page(String category, String priority, Integer enabled, int offset, int limit) {
        return mapper.page(category, priority, enabled, offset, limit);
    }

    public long count(String category, String priority, Integer enabled) {
        return mapper.count(category, priority, enabled);
    }

    public int update(TicketSlaPolicy policy) {
        return mapper.update(policy);
    }

    public int updateEnabled(Long id, Integer enabled) {
        return mapper.updateEnabled(id, enabled);
    }

    public TicketSlaPolicy findBestMatch(String category, String priority) {
        return mapper.findBestMatch(category, priority);
    }
}
