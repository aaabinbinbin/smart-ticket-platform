package com.smartticket.biz.dto.ticket;

import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单详情聚合结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDetailDTO {
    // 工单
    private Ticket ticket;
    // 审批
    private TicketApproval approval;
    // comments
    private List<TicketComment> comments;
    // 操作Logs
    private List<TicketOperationLog> operationLogs;
    // summaries
    private TicketSummaryBundleDTO summaries;
}

