package com.smartticket.biz.dto.assignment;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动分派规则写入命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentRuleCommandDTO {
    /** 规则名称。 */
    private String ruleName;
    /** 适用工单分类，空值表示通配。 */
    private TicketCategoryEnum category;
    /** 适用工单优先级，空值表示通配。 */
    private TicketPriorityEnum priority;
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
}

