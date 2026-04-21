package com.smartticket.api.dto.p1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列创建和更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueRequest {
    /** 队列名称。 */
    @NotBlank(message = "队列名称不能为空")
    @Size(max = 128, message = "队列名称不能超过 128 个字符")
    private String queueName;

    /** 队列编码，创建后不允许修改。 */
    @NotBlank(message = "队列编码不能为空")
    @Size(max = 64, message = "队列编码不能超过 64 个字符")
    private String queueCode;

    /** 所属工单组 ID。 */
    @NotNull(message = "所属工单组不能为空")
    private Long groupId;

    /** 是否启用，空值按启用处理。 */
    private Boolean enabled;
}
