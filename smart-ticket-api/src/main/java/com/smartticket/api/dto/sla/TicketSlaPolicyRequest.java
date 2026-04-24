package com.smartticket.api.dto.sla;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单SLA策略请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaPolicyRequest {
    // 策略Name
    @NotBlank(message = "policyName is required")
    @Size(max = 128, message = "policyName must be <= 128 chars")
    private String policyName;

    // 分类
    private String category;

    // 优先级
    private String priority;

    // first响应Minutes
    @NotNull(message = "firstResponseMinutes is required")
    @Min(value = 1, message = "firstResponseMinutes must be > 0")
    private Integer firstResponseMinutes;

    // 解析Minutes
    @NotNull(message = "resolveMinutes is required")
    @Min(value = 1, message = "resolveMinutes must be > 0")
    private Integer resolveMinutes;

    // 启用
    private Boolean enabled;
}
