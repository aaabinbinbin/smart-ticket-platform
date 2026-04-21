package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单 SLA 实例实体，对应表 {@code ticket_sla_instance}。
 *
 * <p>SLA 实例记录某张工单在某个策略下的响应和解决截止时间。当前阶段不做定时违约扫描。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaInstance {
    /** SLA 实例主键。 */
    private Long id;
    /** 工单 ID。 */
    private Long ticketId;
    /** 命中的 SLA 策略 ID。 */
    private Long policyId;
    /** 首次响应截止时间。 */
    private LocalDateTime firstResponseDeadline;
    /** 解决截止时间。 */
    private LocalDateTime resolveDeadline;
    /** 是否已违约，1-已违约，0-未违约。 */
    private Integer breached;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
