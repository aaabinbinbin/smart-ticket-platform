package com.smartticket.api.dto.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateStepRequestDTO {
    @NotNull(message = "步骤顺序不能为空")
    private Integer stepOrder;

    @NotBlank(message = "步骤名称不能为空")
    private String stepName;

    @NotNull(message = "审批人不能为空")
    private Long approverId;
}
