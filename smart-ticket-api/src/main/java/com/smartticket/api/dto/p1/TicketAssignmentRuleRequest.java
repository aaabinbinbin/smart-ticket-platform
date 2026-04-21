package com.smartticket.api.dto.p1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动分派规则创建和更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentRuleRequest {
    /** 规则名称。 */
    @NotBlank(message = "自动分派规则名称不能为空")
    @Size(max = 128, message = "自动分派规则名称不能超过 128 个字符")
    private String ruleName;

    /** 适用工单分类，空值表示通配。 */
    private String category;

    /** 适用工单优先级，空值表示通配。 */
    private String priority;

    /** 目标工单组 ID。 */
    private Long targetGroupId;

    /** 目标队列 ID。 */
    private Long targetQueueId;

    /** 目标处理人 ID。 */
    private Long targetUserId;

    /** 规则权重。 */
    private Integer weight;

    /** 是否启用，空值按启用处理。 */
    private Boolean enabled;
}
