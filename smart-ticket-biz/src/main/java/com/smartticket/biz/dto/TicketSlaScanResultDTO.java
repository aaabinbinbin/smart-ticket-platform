package com.smartticket.biz.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketSlaScanResultDTO {
    private LocalDateTime scanTime;
    private Integer limit;
    private Integer candidateCount;
    private Integer markedCount;
    private Integer firstResponseBreachedCount;
    private Integer resolveBreachedCount;
    private Integer escalatedCount;
    private Integer notifiedCount;
    private List<Long> breachedInstanceIds;
}