package com.smartticket.biz.repository;

import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.mapper.TicketOperationLogMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单操作日志仓储封装。
 */
@Repository
public class TicketOperationLogRepository {
    private final TicketOperationLogMapper ticketOperationLogMapper;

    public TicketOperationLogRepository(TicketOperationLogMapper ticketOperationLogMapper) {
        this.ticketOperationLogMapper = ticketOperationLogMapper;
    }

    public int insert(TicketOperationLog operationLog) {
        return ticketOperationLogMapper.insert(operationLog);
    }

    public List<TicketOperationLog> findByTicketId(Long ticketId) {
        return ticketOperationLogMapper.findByTicketId(ticketId);
    }

    public long countByOperationType(String operationType) {
        return ticketOperationLogMapper.countByOperationType(operationType);
    }
}
