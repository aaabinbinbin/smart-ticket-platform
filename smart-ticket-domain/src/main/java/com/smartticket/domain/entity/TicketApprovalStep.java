package com.smartticket.domain.entity;

import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalStep {
    private Long id;
    private Long ticketId;
    private Long approvalId;
    private Integer stepOrder;
    private String stepName;
    private Long approverId;
    private TicketApprovalStepStatusEnum stepStatus;
    private String decisionComment;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
