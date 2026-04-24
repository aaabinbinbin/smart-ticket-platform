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
    // submitter摘要
    private TicketSummaryVO submitterSummary;
    // 处理人摘要
    private TicketSummaryVO assigneeSummary;
    // 管理摘要
    private TicketSummaryVO adminSummary;
}
