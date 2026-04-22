package com.smartticket.api.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交工单审批请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "提交工单审批请求")
public class SubmitTicketApprovalRequestDTO {
    @Schema(description = "审批模板 ID，优先按模板生成审批步骤", example = "1")
    private Long templateId;

    @Schema(description = "单步审批时的审批人用户 ID，没有匹配模板时可使用", example = "1002")
    private Long approverId;

    @Size(max = 500, message = "提交说明不能超过 500 个字符")
    @Schema(description = "提交说明", example = "申请测试环境数据库只读权限")
    private String submitComment;
}
