package com.smartticket.api.dto.ticket;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列绑定请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindTicketQueueRequestDTO {
    /** 目标工单组 ID。 */
    @NotNull(message = "工单组不能为空")
    private Long groupId;

    /** 目标工单队列 ID。 */
    @NotNull(message = "工单队列不能为空")
    private Long queueId;
}
