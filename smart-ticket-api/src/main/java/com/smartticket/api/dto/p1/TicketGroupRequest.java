package com.smartticket.api.dto.p1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单组创建和更新请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGroupRequest {
    /** 工单组名称。 */
    @NotBlank(message = "工单组名称不能为空")
    @Size(max = 128, message = "工单组名称不能超过 128 个字符")
    private String groupName;

    /** 工单组编码，创建后不允许修改。 */
    @NotBlank(message = "工单组编码不能为空")
    @Size(max = 64, message = "工单组编码不能超过 64 个字符")
    private String groupCode;

    /** 组负责人用户 ID，可为空。 */
    private Long ownerUserId;

    /** 是否启用，空值按启用处理。 */
    private Boolean enabled;
}
