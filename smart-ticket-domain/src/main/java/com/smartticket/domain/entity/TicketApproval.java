package com.smartticket.domain.entity;

import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApproval {
    private Long id;
    private Long ticketId;
    private Long templateId;
    private Integer currentStepOrder;
    private TicketApprovalStatusEnum approvalStatus;
    private Long approverId;
    private Long requestedBy;
    private String submitComment;
    private String decisionComment;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private TicketApprovalTemplate template;
    private List<TicketApprovalStep> steps;
}
