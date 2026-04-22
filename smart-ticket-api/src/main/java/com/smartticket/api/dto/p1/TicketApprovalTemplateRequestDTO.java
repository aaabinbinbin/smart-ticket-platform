package com.smartticket.api.dto.p1;

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
@Schema(description = "����ģ������")
public class TicketApprovalTemplateRequestDTO {
    @NotBlank(message = "ģ�����Ʋ���Ϊ��")
    private String templateName;

    @NotBlank(message = "�������Ͳ���Ϊ��")
    private String ticketType;

    private String description;

    private Boolean enabled;

    @Valid
    @NotEmpty(message = "�������費��Ϊ��")
    private List<TicketApprovalTemplateStepRequestDTO> steps;
}
