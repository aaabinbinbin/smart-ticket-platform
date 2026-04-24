package com.smartticket.biz.repository.sla;

import com.smartticket.domain.entity.TicketSlaPolicy;
import com.smartticket.domain.mapper.TicketSlaPolicyMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * SLA 策略仓储封装，只负责数据访问。
 */
@Repository
public class TicketSlaPolicyRepository {
    // 映射接口
    private final TicketSlaPolicyMapper mapper;

    /**
     * 构造工单SLA策略仓储。
     */
    public TicketSlaPolicyRepository(TicketSlaPolicyMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketSlaPolicy policy) {
        return mapper.insert(policy);
    }

    /**
     * 查询按ID。
     */
    public TicketSlaPolicy findById(Long id) {
        return mapper.findById(id);
    }

    /**
     * 分页查询。
     */
    public List<TicketSlaPolicy> page(String category, String priority, Integer enabled, int offset, int limit) {
        return mapper.page(category, priority, enabled, offset, limit);
    }

    /**
     * 获取统计信息。
     */
    public long count(String category, String priority, Integer enabled) {
        return mapper.count(category, priority, enabled);
    }

    /**
     * 更新。
     */
    public int update(TicketSlaPolicy policy) {
        return mapper.update(policy);
    }

    /**
     * 更新启用。
     */
    public int updateEnabled(Long id, Integer enabled) {
        return mapper.updateEnabled(id, enabled);
    }

    /**
     * 查询最佳匹配结果。
     */
    public TicketSlaPolicy findBestMatch(String category, String priority) {
        return mapper.findBestMatch(category, priority);
    }
}

