package com.smartticket.api.dto.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批模板步骤请求DTO数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateStepRequestDTO {
    // 步骤Order
    @NotNull(message = "步骤顺序不能为空")
    private Integer stepOrder;

    // 步骤Name
    @NotBlank(message = "步骤名称不能为空")
    private String stepName;

    // 审批人ID
    @NotNull(message = "审批人不能为空")
    private Long approverId;
}
