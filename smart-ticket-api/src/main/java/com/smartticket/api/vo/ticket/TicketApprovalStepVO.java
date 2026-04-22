package com.smartticket.api.vo.ticket;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalStepVO {
    private Long id;
    private Integer stepOrder;
    private String stepName;
    private Long approverId;
    private String stepStatus;
    private String stepStatusInfo;
    private String decisionComment;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
