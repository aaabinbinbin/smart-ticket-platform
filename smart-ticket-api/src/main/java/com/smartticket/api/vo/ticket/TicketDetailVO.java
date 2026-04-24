package com.smartticket.api.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单详情响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工单详情响应对象")
public class TicketDetailVO {
    // 工单
    private TicketVO ticket;
    // 审批
    private TicketApprovalVO approval;
    // comments
    private List<TicketCommentVO> comments;
    // 操作Logs
    private List<TicketOperationLogVO> operationLogs;
    // summaries
    private TicketSummaryBundleVO summaries;
}
