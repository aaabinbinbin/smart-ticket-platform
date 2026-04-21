package com.smartticket.api.dto.p1;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SLA 策略创建和更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketSlaPolicyRequest {
    /** SLA 策略名称。 */
    @NotBlank(message = "SLA 策略名称不能为空")
    @Size(max = 128, message = "SLA 策略名称不能超过 128 个字符")
    private String policyName;

    /** 适用工单分类，空值表示通配。 */
    private String category;

    /** 适用工单优先级，空值表示通配。 */
    private String priority;

    /** 首次响应时限，单位分钟。 */
    @NotNull(message = "首次响应时限不能为空")
    @Min(value = 1, message = "首次响应时限必须大于 0")
    private Integer firstResponseMinutes;

    /** 解决时限，单位分钟。 */
    @NotNull(message = "解决时限不能为空")
    @Min(value = 1, message = "解决时限必须大于 0")
    private Integer resolveMinutes;

    /** 是否启用，空值按启用处理。 */
    private Boolean enabled;
}
