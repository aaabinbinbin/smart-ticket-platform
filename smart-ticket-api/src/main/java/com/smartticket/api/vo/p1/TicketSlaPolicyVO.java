package com.smartticket.api.vo.p1;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SLA 策略响应视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaPolicyVO {
    /** SLA 策略 ID。 */
    private Long id;
    /** SLA 策略名称。 */
    private String policyName;
    /** 适用工单分类。 */
    private String category;
    /** 适用工单优先级。 */
    private String priority;
    /** 首次响应时限，单位分钟。 */
    private Integer firstResponseMinutes;
    /** 解决时限，单位分钟。 */
    private Integer resolveMinutes;
    /** 是否启用。 */
    private Boolean enabled;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
