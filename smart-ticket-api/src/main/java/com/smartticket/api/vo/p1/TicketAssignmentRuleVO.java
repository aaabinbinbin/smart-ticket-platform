package com.smartticket.api.vo.p1;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动分派规则响应视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentRuleVO {
    /** 规则 ID。 */
    private Long id;
    /** 规则名称。 */
    private String ruleName;
    /** 适用工单分类。 */
    private String category;
    /** 适用工单优先级。 */
    private String priority;
    /** 目标工单组 ID。 */
    private Long targetGroupId;
    /** 目标队列 ID。 */
    private Long targetQueueId;
    /** 目标处理人 ID。 */
    private Long targetUserId;
    /** 规则权重。 */
    private Integer weight;
    /** 是否启用。 */
    private Boolean enabled;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
