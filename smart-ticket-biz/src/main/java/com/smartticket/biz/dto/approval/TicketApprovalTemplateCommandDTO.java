package com.smartticket.biz.dto.approval;

import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批模板命令DTO数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateCommandDTO {
    // 模板Name
    private String templateName;
    // 工单类型
    private TicketTypeEnum ticketType;
    // 描述
    private String description;
    // 启用
    private Boolean enabled;
    // steps
    private List<TicketApprovalTemplateStepCommandDTO> steps;
}

