package com.smartticket.biz.dto.ticket;

import com.smartticket.domain.enums.TicketStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新工单状态命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketUpdateStatusCommandDTO {
    // 目标状态
    private TicketStatusEnum targetStatus;
    // 解决摘要
    private String solutionSummary;
}

