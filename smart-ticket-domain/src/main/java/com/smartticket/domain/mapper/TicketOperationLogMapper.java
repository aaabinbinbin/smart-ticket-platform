package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketOperationLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单操作日志 Mapper，对应表 {@code ticket_operation_log}。
 */
@Mapper
public interface TicketOperationLogMapper {

    int insert(TicketOperationLog ticketOperationLog);

    TicketOperationLog findById(@Param("id") Long id);

    List<TicketOperationLog> findByTicketId(@Param("ticketId") Long ticketId);
}
