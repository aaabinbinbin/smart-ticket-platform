package com.smartticket.api.vo.p1;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单 SLA 实例响应视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaInstanceVO {
    /** SLA 实例 ID。 */
    private Long id;
    /** 工单 ID。 */
    private Long ticketId;
    /** SLA 策略 ID。 */
    private Long policyId;
    /** 首次响应截止时间。 */
    private LocalDateTime firstResponseDeadline;
    /** 解决截止时间。 */
    private LocalDateTime resolveDeadline;
    /** 是否已违约。 */
    private Boolean breached;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
