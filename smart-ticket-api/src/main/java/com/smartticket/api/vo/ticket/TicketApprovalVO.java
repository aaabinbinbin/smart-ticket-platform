package com.smartticket.api.vo.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批响应对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工单审批响应对象")
public class TicketApprovalVO {
    private Long id;
    private Long ticketId;
    private Long templateId;
    private Integer currentStepOrder;
    private String approvalStatus;
    private String approvalStatusInfo;
    private Long approverId;
    private Long requestedBy;
    private String submitComment;
    private String decisionComment;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TicketApprovalStepVO> steps;
}
