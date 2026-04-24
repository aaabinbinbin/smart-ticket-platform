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
    // view
    private TicketSummaryViewEnum view;
    // 标题
    private String title;
    // 摘要
    private String summary;
    // highlights
    private List<String> highlights;
    // riskLevel
    private String riskLevel;
    // generated时间
    private LocalDateTime generatedAt;
}

