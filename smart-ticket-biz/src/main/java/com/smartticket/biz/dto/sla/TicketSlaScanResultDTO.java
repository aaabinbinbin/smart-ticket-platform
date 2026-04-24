package com.smartticket.biz.dto.sla;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 工单SLAScan结果DTO数据传输对象。
 */
@Data
@Builder
public class TicketSlaScanResultDTO {
    // scanTime
    private LocalDateTime scanTime;
    // limit
    private Integer limit;
    // 候选统计
    private Integer candidateCount;
    // marked统计
    private Integer markedCount;
    // first响应Breached统计
    private Integer firstResponseBreachedCount;
    // 解析Breached统计
    private Integer resolveBreachedCount;
    // escalated统计
    private Integer escalatedCount;
    // notified统计
    private Integer notifiedCount;
    // breachedInstanceIds
    private List<Long> breachedInstanceIds;
}
