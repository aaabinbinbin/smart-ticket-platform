package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateStep {
    private Long id;
    private Long templateId;
    private Integer stepOrder;
    private String stepName;
    private Long approverId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
