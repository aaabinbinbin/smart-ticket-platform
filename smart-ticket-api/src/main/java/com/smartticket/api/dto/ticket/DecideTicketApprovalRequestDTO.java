package com.smartticket.api.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批通过或驳回请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批决定请求")
public class DecideTicketApprovalRequestDTO {
    // 决策评论
    @Size(max = 500, message = "审批说明不能超过 500 个字符")
    @Schema(description = "审批说明", example = "已核对申请用途，同意开通")
    private String decisionComment;
}
