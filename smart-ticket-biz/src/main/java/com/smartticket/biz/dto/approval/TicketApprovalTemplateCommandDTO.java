package com.smartticket.biz.dto.approval;

import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateCommandDTO {
    private String templateName;
    private TicketTypeEnum ticketType;
    private String description;
    private Boolean enabled;
    private List<TicketApprovalTemplateStepCommandDTO> steps;
}

