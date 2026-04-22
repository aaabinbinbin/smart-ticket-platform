package com.smartticket.biz.dto.ticket;

import com.smartticket.domain.enums.TicketSummaryViewEnum;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单一视角的工单摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSummaryDTO {
    private TicketSummaryViewEnum view;
    private String title;
    private String summary;
    private List<String> highlights;
    private String riskLevel;
    private LocalDateTime generatedAt;
}

