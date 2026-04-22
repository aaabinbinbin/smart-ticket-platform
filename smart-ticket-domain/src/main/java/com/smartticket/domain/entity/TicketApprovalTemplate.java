package com.smartticket.domain.entity;

import com.smartticket.domain.enums.TicketTypeEnum;
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
public class TicketApprovalTemplate {
    private Long id;
    private String templateName;
    private TicketTypeEnum ticketType;
    private String description;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TicketApprovalTemplateStep> steps;
}
