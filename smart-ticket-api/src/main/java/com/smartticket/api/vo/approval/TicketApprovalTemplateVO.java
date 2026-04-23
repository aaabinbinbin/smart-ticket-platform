package com.smartticket.api.vo.approval;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateVO {
    private Long id;
    private String templateName;
    private String ticketType;
    private String ticketTypeInfo;
    private String description;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TicketApprovalTemplateStepVO> steps;
}
