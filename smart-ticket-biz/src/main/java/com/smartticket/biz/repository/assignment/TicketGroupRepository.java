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
    // 工单分组映射接口
    private final TicketGroupMapper ticketGroupMapper;

    /**
     * 构造工单分组仓储。
     */
    public TicketGroupRepository(TicketGroupMapper ticketGroupMapper) {
        this.ticketGroupMapper = ticketGroupMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketGroup ticketGroup) {
        return ticketGroupMapper.insert(ticketGroup);
    }

    /**
     * 查询按ID。
     */
    public TicketGroup findById(Long id) {
        return ticketGroupMapper.findById(id);
    }

    /**
     * 查询按编码。
     */
    public TicketGroup findByCode(String groupCode) {
        return ticketGroupMapper.findByCode(groupCode);
    }

    /**
     * 分页查询。
     */
    public List<TicketGroup> page(String keyword, Integer enabled, int offset, int limit) {
        return ticketGroupMapper.page(keyword, enabled, offset, limit);
    }

    /**
     * 获取统计信息。
     */
    public long count(String keyword, Integer enabled) {
        return ticketGroupMapper.count(keyword, enabled);
    }

    /**
     * 更新。
     */
    public int update(TicketGroup ticketGroup) {
        return ticketGroupMapper.update(ticketGroup);
    }

    /**
     * 更新启用。
     */
    public int updateEnabled(Long id, Integer enabled) {
        return ticketGroupMapper.updateEnabled(id, enabled);
    }
}

