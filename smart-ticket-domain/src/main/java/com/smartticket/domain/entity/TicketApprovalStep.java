package com.smartticket.domain.entity;

import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批步骤类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalStep {
    // ID
    private Long id;
    // 工单ID
    private Long ticketId;
    // 审批ID
    private Long approvalId;
    // 步骤Order
    private Integer stepOrder;
    // 步骤Name
    private String stepName;
    // 审批人ID
    private Long approverId;
    // 步骤状态
    private TicketApprovalStepStatusEnum stepStatus;
    // 决策评论
    private String decisionComment;
    // decided时间
    private LocalDateTime decidedAt;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
