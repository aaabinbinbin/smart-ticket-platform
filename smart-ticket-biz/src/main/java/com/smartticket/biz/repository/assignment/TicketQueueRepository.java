package com.smartticket.biz.repository.assignment;

import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.mapper.TicketQueueMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单队列仓储封装，只负责数据访问。
 */
@Repository
public class TicketQueueRepository {
    // 工单队列映射接口
    private final TicketQueueMapper ticketQueueMapper;

    /**
     * 构造工单队列仓储。
     */
    public TicketQueueRepository(TicketQueueMapper ticketQueueMapper) {
        this.ticketQueueMapper = ticketQueueMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketQueue ticketQueue) {
        return ticketQueueMapper.insert(ticketQueue);
    }

    /**
     * 查询按ID。
     */
    public TicketQueue findById(Long id) {
        return ticketQueueMapper.findById(id);
    }

    /**
     * 查询按编码。
     */
    public TicketQueue findByCode(String queueCode) {
        return ticketQueueMapper.findByCode(queueCode);
    }

    /**
     * 查询启用按分组ID。
     */
    public List<TicketQueue> findEnabledByGroupId(Long groupId) {
        return ticketQueueMapper.findEnabledByGroupId(groupId);
    }

    /**
     * 分页查询。
     */
    public List<TicketQueue> page(Long groupId, String keyword, Integer enabled, int offset, int limit) {
        return ticketQueueMapper.page(groupId, keyword, enabled, offset, limit);
    }

    /**
     * 获取统计信息。
     */
    public long count(Long groupId, String keyword, Integer enabled) {
        return ticketQueueMapper.count(groupId, keyword, enabled);
    }

    /**
     * 更新。
     */
    public int update(TicketQueue ticketQueue) {
        return ticketQueueMapper.update(ticketQueue);
    }

    /**
     * 更新启用。
     */
    public int updateEnabled(Long id, Integer enabled) {
        return ticketQueueMapper.updateEnabled(id, enabled);
    }
}

