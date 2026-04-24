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
    // ID
    private Long id;
    // 工单ID
    private Long ticketId;
    // 模板ID
    private Long templateId;
    // 当前步骤Order
    private Integer currentStepOrder;
    // 审批状态
    private String approvalStatus;
    // 审批状态Info
    private String approvalStatusInfo;
    // 审批人ID
    private Long approverId;
    // requested按
    private Long requestedBy;
    // submit评论
    private String submitComment;
    // 决策评论
    private String decisionComment;
    // submitted时间
    private LocalDateTime submittedAt;
    // decided时间
    private LocalDateTime decidedAt;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
    // steps
    private List<TicketApprovalStepVO> steps;
}
