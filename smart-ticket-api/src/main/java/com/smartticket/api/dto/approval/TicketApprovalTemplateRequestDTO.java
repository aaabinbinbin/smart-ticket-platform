package com.smartticket.api.dto.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批模板请求")
public class TicketApprovalTemplateRequestDTO {
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @NotBlank(message = "工单类型不能为空")
    private String ticketType;

    private String description;

    private Boolean enabled;

    @Valid
    @NotEmpty(message = "审批步骤不能为空")
    private List<TicketApprovalTemplateStepRequestDTO> steps;
}
