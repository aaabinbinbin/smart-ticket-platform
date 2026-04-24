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
    // 映射接口
    private final TicketSlaInstanceMapper mapper;

    /**
     * 构造工单SLAInstance仓储。
     */
    public TicketSlaInstanceRepository(TicketSlaInstanceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketSlaInstance instance) {
        return mapper.insert(instance);
    }

    /**
     * 查询按ID。
     */
    public TicketSlaInstance findById(Long id) {
        return mapper.findById(id);
    }

    /**
     * 查询按工单ID。
     */
    public TicketSlaInstance findByTicketId(Long ticketId) {
        return mapper.findByTicketId(ticketId);
    }

    /**
     * 更新按工单ID。
     */
    public int updateByTicketId(TicketSlaInstance instance) {
        return mapper.updateByTicketId(instance);
    }

    /**
     * 查询BreachedCandidates。
     */
    public List<TicketSlaInstance> findBreachedCandidates(LocalDateTime now, int limit) {
        return mapper.findBreachedCandidates(now, limit);
    }

    /**
     * 处理Breached。
     */
    public int markBreached(Long id) {
        return mapper.markBreached(id);
    }
}

