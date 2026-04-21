package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列实体，对应表 {@code ticket_queue}。
 *
 * <p>队列隶属于工单组，用于后续承接 SLA、分派规则和队列视图。当前阶段只实现配置管理。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueue {
    /** 队列主键。 */
    private Long id;
    /** 队列名称。 */
    private String queueName;
    /** 队列编码，全局唯一。 */
    private String queueCode;
    /** 所属工单组 ID。 */
    private Long groupId;
    /** 是否启用，1-启用，0-停用。 */
    private Integer enabled;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
