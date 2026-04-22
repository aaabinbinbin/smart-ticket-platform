package com.smartticket.api.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多视角工单摘要响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "多视角工单摘要响应对象")
public class TicketSummaryBundleVO {
    private TicketSummaryVO submitterSummary;
    private TicketSummaryVO assigneeSummary;
    private TicketSummaryVO adminSummary;
}
