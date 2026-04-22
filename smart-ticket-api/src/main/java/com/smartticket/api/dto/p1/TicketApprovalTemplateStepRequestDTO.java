package com.smartticket.api.dto.p1;

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
    @NotNull(message = "����˳����Ϊ��")
    private Integer stepOrder;

    @NotBlank(message = "�������Ʋ���Ϊ��")
    private String stepName;

    @NotNull(message = "�����˲���Ϊ��")
    private Long approverId;
}
