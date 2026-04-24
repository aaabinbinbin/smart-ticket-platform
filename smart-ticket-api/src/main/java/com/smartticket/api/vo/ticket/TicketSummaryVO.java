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
    // view
    private String view;
    // viewInfo
    private String viewInfo;
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
