package com.smartticket.biz.dto.assignment;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单Assignment统计DTO数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentStatsDTO {
    // auto分派Matched统计
    private long autoAssignMatchedCount;
    // auto分派Fallback统计
    private long autoAssignFallbackCount;
    // auto分派Pending统计
    private long autoAssignPendingCount;
    // claimed统计
    private long claimedCount;
    // totalAuto分派统计
    private long totalAutoAssignCount;
    // autoAssigned统计
    private long autoAssignedCount;
    // auto分派HitRate
    private BigDecimal autoAssignHitRate;
}

