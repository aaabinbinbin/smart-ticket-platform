package com.smartticket.api.dto.p1;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMemberRequest {
    @NotNull(message = "成员用户不能为空")
    private Long userId;

    private Boolean enabled;
}
