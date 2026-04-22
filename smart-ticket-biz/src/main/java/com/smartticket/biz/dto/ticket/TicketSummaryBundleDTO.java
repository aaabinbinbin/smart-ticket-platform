package com.smartticket.biz.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多视角工单摘要聚合结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSummaryBundleDTO {
    private TicketSummaryDTO submitterSummary;
    private TicketSummaryDTO assigneeSummary;
    private TicketSummaryDTO adminSummary;
}

