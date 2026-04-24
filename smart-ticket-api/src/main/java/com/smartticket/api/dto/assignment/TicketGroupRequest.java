package com.smartticket.api.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单分组请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGroupRequest {
    // 分组Name
    @NotBlank(message = "groupName is required")
    @Size(max = 128, message = "groupName must be <= 128 chars")
    private String groupName;

    // 分组编码
    @NotBlank(message = "groupCode is required")
    @Size(max = 64, message = "groupCode must be <= 64 chars")
    private String groupCode;

    // owner用户ID
    private Long ownerUserId;

    // 启用
    private Boolean enabled;
}
