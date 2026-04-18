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
    private TicketVO ticket;
    private List<TicketCommentVO> comments;
    private List<TicketOperationLogVO> operationLogs;
}
