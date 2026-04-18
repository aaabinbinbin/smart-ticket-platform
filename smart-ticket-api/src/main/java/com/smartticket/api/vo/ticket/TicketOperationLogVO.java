package com.smartticket.api.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单操作日志响应对象。
 *
 * <p>用于详情页展示工单关键操作轨迹，包括操作类型、操作说明和变更前后快照。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工单操作日志响应对象")
public class TicketOperationLogVO {
    private Long id;
    private Long ticketId;
    private Long operatorId;
    private String operationType;
    private String operationTypeInfo;
    private String operationDesc;
    private String beforeValue;
    private String afterValue;
    private LocalDateTime createdAt;
}
