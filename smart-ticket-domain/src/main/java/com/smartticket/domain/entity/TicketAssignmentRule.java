package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单自动分派规则实体，对应表 {@code ticket_assignment_rule}。
 *
 * <p>当前 P1 只用于 preview 推荐，不直接执行真实分派。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentRule {
    /** 分派规则主键。 */
    private Long id;
    /** 规则名称。 */
    private String ruleName;
    /** 适用工单分类，空值表示通配。 */
    private String category;
    /** 适用工单优先级，空值表示通配。 */
    private String priority;
    /** 目标工单组 ID，可为空。 */
    private Long targetGroupId;
    /** 目标队列 ID，可为空。 */
    private Long targetQueueId;
    /** 目标处理人 ID，可为空。 */
    private Long targetUserId;
    /** 规则权重，越大优先级越高。 */
    private Integer weight;
    /** 是否启用，1-启用，0-停用。 */
    private Integer enabled;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
