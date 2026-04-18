package com.smartticket.domain.entity;

import com.smartticket.domain.enums.OperationTypeEnum;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单操作日志实体，对应表 {@code ticket_operation_log}。
 *
 * <p>操作日志用于审计和追溯，记录工单创建、分配、转派、状态变更、评论、关闭等关键动作。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketOperationLog {
    /** 日志主键。 */
    private Long id;
    /** 所属工单 ID。 */
    private Long ticketId;
    /** 操作人用户 ID。 */
    private Long operatorId;
    /** 操作类型。 */
    private OperationTypeEnum operationType;
    /** 操作说明。 */
    private String operationDesc;
    /** 变更前内容，可存 JSON 或文本。 */
    private String beforeValue;
    /** 变更后内容，可存 JSON 或文本。 */
    private String afterValue;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
