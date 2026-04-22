package com.smartticket.api.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单摘要响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工单摘要响应对象")
public class TicketSummaryVO {
    private String view;
    private String viewInfo;
    private String title;
    private String summary;
    private List<String> highlights;
    private String riskLevel;
    private LocalDateTime generatedAt;
}
