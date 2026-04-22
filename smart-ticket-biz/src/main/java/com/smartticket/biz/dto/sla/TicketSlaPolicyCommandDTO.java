package com.smartticket.biz.dto.sla;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SLA 策略写入命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaPolicyCommandDTO {
    /** SLA 策略名称。 */
    private String policyName;
    /** 适用工单分类，空值表示通配。 */
    private TicketCategoryEnum category;
    /** 适用工单优先级，空值表示通配。 */
    private TicketPriorityEnum priority;
    /** 首次响应时限，单位分钟。 */
    private Integer firstResponseMinutes;
    /** 解决时限，单位分钟。 */
    private Integer resolveMinutes;
    /** 是否启用，空值按启用处理。 */
    private Boolean enabled;
}

