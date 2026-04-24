package com.smartticket.api.vo.approval;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批模板VO视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplateVO {
    // ID
    private Long id;
    // 模板Name
    private String templateName;
    // 工单类型
    private String ticketType;
    // 工单类型Info
    private String ticketTypeInfo;
    // 描述
    private String description;
    // 启用
    private Boolean enabled;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
    // steps
    private List<TicketApprovalTemplateStepVO> steps;
}
