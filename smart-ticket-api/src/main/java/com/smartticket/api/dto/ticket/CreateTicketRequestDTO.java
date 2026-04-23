package com.smartticket.api.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @NotBlank(message = "工单描述不能为空")
    @Schema(description = "工单描述", example = "测试环境登录时报 500，影响研发自测")
    private String description;

    @Schema(description = "工单类型 code", example = "INCIDENT")
    private String type;

    @Schema(description = "工单类型扩展字段，用于提交差异化信息")
    private Map<String, Object> typeProfile;

    @Schema(description = "工单分类 code，缺省时可由系统自动推断", example = "SYSTEM")
    private String category;

    @Schema(description = "工单优先级 code，缺省时系统可使用默认值", example = "HIGH")
    private String priority;

    @Size(max = 128, message = "幂等键不能超过 128 个字符")
    @Schema(description = "请求幂等键", example = "create-ticket-001")
    private String idempotencyKey;
}
