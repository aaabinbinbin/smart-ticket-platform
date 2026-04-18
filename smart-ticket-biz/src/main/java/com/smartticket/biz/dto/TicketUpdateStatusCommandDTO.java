package com.smartticket.biz.dto;

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
    private TicketStatusEnum targetStatus;
    private String solutionSummary;
}
