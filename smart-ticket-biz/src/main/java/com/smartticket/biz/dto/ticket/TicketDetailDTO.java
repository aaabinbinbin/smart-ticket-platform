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
    private Ticket ticket;
    private TicketApproval approval;
    private List<TicketComment> comments;
    private List<TicketOperationLog> operationLogs;
    private TicketSummaryBundleDTO summaries;
}

