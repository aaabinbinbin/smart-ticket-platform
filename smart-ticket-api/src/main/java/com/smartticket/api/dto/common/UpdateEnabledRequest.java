package com.smartticket.api.dto.common;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新启用请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEnabledRequest {
    // 启用
    @NotNull(message = "enabled is required")
    private Boolean enabled;
}
