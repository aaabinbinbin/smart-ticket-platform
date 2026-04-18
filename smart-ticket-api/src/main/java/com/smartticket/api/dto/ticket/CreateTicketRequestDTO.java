package com.smartticket.api.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建工单请求体。
 *
 * <p>前端通过枚举 code 传递分类和优先级，例如 SYSTEM、HIGH。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建工单请求")
public class CreateTicketRequestDTO {
    @NotBlank(message = "工单标题不能为空")
    @Size(max = 200, message = "工单标题不能超过 200 个字符")
    @Schema(description = "工单标题", example = "测试环境无法登录")
    private String title;

    @NotBlank(message = "问题描述不能为空")
    @Schema(description = "问题描述", example = "测试环境登录时报 500，影响研发自测")
    private String description;

    @NotBlank(message = "工单分类不能为空")
    @Schema(description = "工单分类 code", example = "SYSTEM")
    private String category;

    @NotBlank(message = "工单优先级不能为空")
    @Schema(description = "工单优先级 code", example = "HIGH")
    private String priority;

    @Size(max = 128, message = "幂等键不能超过 128 个字符")
    @Schema(description = "创建幂等键", example = "create-ticket-001")
    private String idempotencyKey;
}
