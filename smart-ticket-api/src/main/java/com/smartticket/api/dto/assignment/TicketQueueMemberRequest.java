package com.smartticket.api.dto.assignment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列成员请求对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMemberRequest {
    // 用户ID
    @NotNull(message = "成员用户不能为空")
    private Long userId;

    // 启用
    private Boolean enabled;
}
