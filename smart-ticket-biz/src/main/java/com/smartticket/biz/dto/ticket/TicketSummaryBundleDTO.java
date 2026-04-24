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
    // submitter摘要
    private TicketSummaryDTO submitterSummary;
    // 处理人摘要
    private TicketSummaryDTO assigneeSummary;
    // 管理摘要
    private TicketSummaryDTO adminSummary;
}

