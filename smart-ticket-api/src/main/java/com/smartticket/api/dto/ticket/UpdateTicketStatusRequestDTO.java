package com.smartticket.api.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新工单状态请求体。
 *
 * <p>该请求用于通用状态流转接口。关闭工单有独立业务语义，需要走关闭接口。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新工单状态请求")
public class UpdateTicketStatusRequestDTO {
    @NotBlank(message = "目标状态不能为空")
    @Schema(description = "目标状态 code。通用状态接口只允许 PROCESSING 或 RESOLVED；CLOSED 必须走关闭接口", example = "RESOLVED")
    private String targetStatus;

    @Schema(description = "解决方案摘要，通常在更新为 RESOLVED 时填写", example = "重启登录服务后恢复")
    private String solutionSummary;
}
