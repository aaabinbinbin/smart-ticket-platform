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
    // ID
    private Long id;
    // 工单ID
    private Long ticketId;
    // 操作人ID
    private Long operatorId;
    // 操作类型
    private String operationType;
    // 操作类型Info
    private String operationTypeInfo;
    // 操作Desc
    private String operationDesc;
    // beforeValue
    private String beforeValue;
    // afterValue
    private String afterValue;
    // 创建时间
    private LocalDateTime createdAt;
}
