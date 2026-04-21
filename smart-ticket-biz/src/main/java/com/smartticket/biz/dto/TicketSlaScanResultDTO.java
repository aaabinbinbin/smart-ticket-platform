package com.smartticket.biz.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * SLA 违约扫描结果。
 *
 * <p>该对象只描述本次扫描命中的 SLA 实例与标记结果，不承载通知、升级或工单状态流转信息。</p>
 */
@Data
@Builder
public class TicketSlaScanResultDTO {
    /** 本次扫描使用的业务时间。 */
    private LocalDateTime scanTime;

    /** 本次扫描的最大处理数量。 */
    private Integer limit;

    /** 查询到的候选违约实例数量。 */
    private Integer candidateCount;

    /** 成功标记为违约的实例数量。 */
    private Integer markedCount;

    /** 本次成功标记违约的 SLA 实例 ID。 */
    private List<Long> breachedInstanceIds;
}
