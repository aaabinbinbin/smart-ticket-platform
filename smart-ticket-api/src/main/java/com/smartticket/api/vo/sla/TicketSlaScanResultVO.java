package com.smartticket.api.vo.sla;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 工单SLAScan结果VO视图对象。
 */
@Data
@Builder
@Schema(description = "SLA breach scan result")
public class TicketSlaScanResultVO {
    // scanTime
    @Schema(description = "Scan time")
    private LocalDateTime scanTime;

    // limit
    @Schema(description = "Scan limit")
    private Integer limit;

    // 候选统计
    @Schema(description = "Candidate count")
    private Integer candidateCount;

    // marked统计
    @Schema(description = "Marked count")
    private Integer markedCount;

    // first响应Breached统计
    @Schema(description = "First response breached count")
    private Integer firstResponseBreachedCount;

    // 解析Breached统计
    @Schema(description = "Resolve breached count")
    private Integer resolveBreachedCount;

    // escalated统计
    @Schema(description = "Escalated count")
    private Integer escalatedCount;

    // notified统计
    @Schema(description = "Notified count")
    private Integer notifiedCount;

    // breachedInstanceIds
    @Schema(description = "Breached SLA instance ids")
    private List<Long> breachedInstanceIds;
}
