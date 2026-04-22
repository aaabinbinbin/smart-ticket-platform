package com.smartticket.biz.dto.approval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateStepCommandDTO {
    private Integer stepOrder;
    private String stepName;
    private Long approverId;
}

