package com.smartticket.api.vo.approval;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateStepVO {
    private Long id;
    private Integer stepOrder;
    private String stepName;
    private Long approverId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
