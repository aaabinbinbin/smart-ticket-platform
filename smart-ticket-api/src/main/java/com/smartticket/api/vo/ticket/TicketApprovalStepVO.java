package com.smartticket.api.vo.ticket;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批步骤VO视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalStepVO {
    // ID
    private Long id;
    // 步骤Order
    private Integer stepOrder;
    // 步骤Name
    private String stepName;
    // 审批人ID
    private Long approverId;
    // 步骤状态
    private String stepStatus;
    // 步骤状态Info
    private String stepStatusInfo;
    // 决策评论
    private String decisionComment;
    // decided时间
    private LocalDateTime decidedAt;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
