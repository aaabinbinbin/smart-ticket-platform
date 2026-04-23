package com.smartticket.api.vo.sla;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "SLA breach scan result")
public class TicketSlaScanResultVO {
    @Schema(description = "Scan time")
    private LocalDateTime scanTime;

    @Schema(description = "Scan limit")
    private Integer limit;

    @Schema(description = "Candidate count")
    private Integer candidateCount;

    @Schema(description = "Marked count")
    private Integer markedCount;

    @Schema(description = "First response breached count")
    private Integer firstResponseBreachedCount;

    @Schema(description = "Resolve breached count")
    private Integer resolveBreachedCount;

    @Schema(description = "Escalated count")
    private Integer escalatedCount;

    @Schema(description = "Notified count")
    private Integer notifiedCount;

    @Schema(description = "Breached SLA instance ids")
    private List<Long> breachedInstanceIds;
}
