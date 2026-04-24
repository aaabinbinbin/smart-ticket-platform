package com.smartticket.biz.repository.ticket;

import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.mapper.TicketOperationLogMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单操作日志仓储封装。
 */
@Repository
public class TicketOperationLogRepository {
    // 工单操作Log映射接口
    private final TicketOperationLogMapper ticketOperationLogMapper;

    /**
     * 构造工单操作日志仓储。
     */
    public TicketOperationLogRepository(TicketOperationLogMapper ticketOperationLogMapper) {
        this.ticketOperationLogMapper = ticketOperationLogMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketOperationLog operationLog) {
        return ticketOperationLogMapper.insert(operationLog);
    }

    /**
     * 查询按工单ID。
     */
    public List<TicketOperationLog> findByTicketId(Long ticketId) {
        return ticketOperationLogMapper.findByTicketId(ticketId);
    }

    /**
     * 统计按操作类型。
     */
    public long countByOperationType(String operationType) {
        return ticketOperationLogMapper.countByOperationType(operationType);
    }
}

