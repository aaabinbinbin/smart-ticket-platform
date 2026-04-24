package com.smartticket.api.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分配或转派工单请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分配或转派工单请求")
public class AssignTicketRequestDTO {
    // 处理人ID
    @NotNull(message = "处理人 ID 不能为空")
    @Schema(description = "目标处理人用户 ID，必须具备 STAFF 角色", example = "2")
    private Long assigneeId;
}
