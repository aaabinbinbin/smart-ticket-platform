package com.smartticket.domain.entity;

import com.smartticket.domain.enums.TicketTypeEnum;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批模板类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplate {
    // ID
    private Long id;
    // 模板Name
    private String templateName;
    // 工单类型
    private TicketTypeEnum ticketType;
    // 描述
    private String description;
    // 启用
    private Integer enabled;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
    // steps
    private List<TicketApprovalTemplateStep> steps;
}
