package com.smartticket.api.dto.p1;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 启停状态更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEnabledRequest {
    /** 是否启用。 */
    @NotNull(message = "启停状态不能为空")
    private Boolean enabled;
}
