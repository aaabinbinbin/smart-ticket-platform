package com.smartticket.api.vo.p1;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentStatsVO {
    private long autoAssignMatchedCount;
    private long autoAssignFallbackCount;
    private long autoAssignPendingCount;
    private long claimedCount;
    private long totalAutoAssignCount;
    private long autoAssignedCount;
    private BigDecimal autoAssignHitRate;
}
