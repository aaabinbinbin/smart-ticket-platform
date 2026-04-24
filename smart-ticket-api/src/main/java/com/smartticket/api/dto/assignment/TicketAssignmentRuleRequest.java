package com.smartticket.api.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单分派规则请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentRuleRequest {
    // 规则Name
    @NotBlank(message = "ruleName is required")
    @Size(max = 128, message = "ruleName must be <= 128 chars")
    private String ruleName;

    // 分类
    private String category;

    // 优先级
    private String priority;

    // 目标分组ID
    private Long targetGroupId;

    // 目标队列ID
    private Long targetQueueId;

    // 目标用户ID
    private Long targetUserId;

    // weight
    private Integer weight;

    // 启用
    private Boolean enabled;
}
