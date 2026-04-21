package com.smartticket.api.vo.p1;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * SLA 违约扫描结果响应。
 */
@Data
@Builder
@Schema(description = "SLA 违约扫描结果")
public class TicketSlaScanResultVO {
    /** 本次扫描使用的业务时间。 */
    @Schema(description = "本次扫描使用的业务时间")
    private LocalDateTime scanTime;

    /** 本次扫描的最大处理数量。 */
    @Schema(description = "本次扫描的最大处理数量")
    private Integer limit;

    /** 查询到的候选违约实例数量。 */
    @Schema(description = "查询到的候选违约实例数量")
    private Integer candidateCount;

    /** 成功标记为违约的实例数量。 */
    @Schema(description = "成功标记为违约的实例数量")
    private Integer markedCount;

    /** 本次成功标记违约的 SLA 实例 ID。 */
    @Schema(description = "本次成功标记违约的 SLA 实例 ID")
    private List<Long> breachedInstanceIds;
}
