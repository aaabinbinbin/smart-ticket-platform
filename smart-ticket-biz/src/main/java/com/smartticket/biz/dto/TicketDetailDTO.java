package com.smartticket.biz.dto;

import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单详情聚合结果。
 *
 * <p>业务层返回实体聚合，api 层负责转换成 HTTP VO。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDetailDTO {
    private Ticket ticket;
    private List<TicketComment> comments;
    private List<TicketOperationLog> operationLogs;
}
