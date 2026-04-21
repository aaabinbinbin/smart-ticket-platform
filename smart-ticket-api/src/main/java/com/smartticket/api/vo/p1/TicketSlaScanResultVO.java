package com.smartticket.api.vo.p1;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "SLA 扫描结果")
public class TicketSlaScanResultVO {
    @Schema(description = "扫描时间")
    private LocalDateTime scanTime;

    @Schema(description = "扫描上限")
    private Integer limit;

    @Schema(description = "候选实例数量")
    private Integer candidateCount;

    @Schema(description = "成功标记违约数量")
    private Integer markedCount;

    @Schema(description = "首次响应违约数量")
    private Integer firstResponseBreachedCount;

    @Schema(description = "解决时限违约数量")
    private Integer resolveBreachedCount;

    @Schema(description = "成功升级数量")
    private Integer escalatedCount;

    @Schema(description = "成功通知数量")
    private Integer notifiedCount;

    @Schema(description = "成功标记违约的 SLA 实例 ID")
    private List<Long> breachedInstanceIds;
}