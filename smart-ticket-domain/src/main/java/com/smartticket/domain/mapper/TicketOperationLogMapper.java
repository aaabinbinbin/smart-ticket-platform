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

    /**
     * 处理新增。
     */
    int insert(TicketOperationLog ticketOperationLog);

    /**
     * 查询按ID。
     */
    TicketOperationLog findById(@Param("id") Long id);

    /**
     * 查询按工单ID。
     */
    List<TicketOperationLog> findByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 统计按操作类型。
     */
    long countByOperationType(@Param("operationType") String operationType);
}
