package com.smartticket.api.vo.approval;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批模板步骤VO视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateStepVO {
    // ID
    private Long id;
    // 步骤Order
    private Integer stepOrder;
    // 步骤Name
    private String stepName;
    // 审批人ID
    private Long approverId;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
