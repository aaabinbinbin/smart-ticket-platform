package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单 SLA 策略实体，对应表 {@code ticket_sla_policy}。
 *
 * <p>SLA 策略用于按工单分类和优先级匹配时限规则。当前 P1 第一版只支持基础配置和最小匹配。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaPolicy {
    /** SLA 策略主键。 */
    private Long id;
    /** SLA 策略名称。 */
    private String policyName;
    /** 适用工单分类，空值表示通配。 */
    private String category;
    /** 适用工单优先级，空值表示通配。 */
    private String priority;
    /** 首次响应时限，单位分钟。 */
    private Integer firstResponseMinutes;
    /** 解决时限，单位分钟。 */
    private Integer resolveMinutes;
    /** 是否启用，1-启用，0-停用。 */
    private Integer enabled;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
