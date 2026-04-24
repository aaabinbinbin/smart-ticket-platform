package com.smartticket.biz.dto.approval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批模板步骤命令DTO数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateStepCommandDTO {
    // 步骤Order
    private Integer stepOrder;
    // 步骤Name
    private String stepName;
    // 审批人ID
    private Long approverId;
}

