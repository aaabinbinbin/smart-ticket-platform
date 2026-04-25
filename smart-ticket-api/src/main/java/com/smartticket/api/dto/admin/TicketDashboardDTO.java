package com.smartticket.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单 Dashboard 指标 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDashboardDTO {
    /** 待分配工单数。 */
    private long pendingAssignCount;
    /** 处理中工单数。 */
    private long processingCount;
    /** 已解决工单数。 */
    private long resolvedCount;
    /** 已关闭工单数。 */
    private long closedCount;
    /** 今日创建工单数。 */
    private long todayCreatedCount;
}
