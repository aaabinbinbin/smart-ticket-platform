package com.smartticket.biz.dto.approval;

import com.smartticket.domain.enums.TicketTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单审批模板分页查询DTO数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplatePageQueryDTO {
    // 分页编号
    private int pageNo;
    // 分页Size
    private int pageSize;
    // 工单类型
    private TicketTypeEnum ticketType;
    // 启用
    private Boolean enabled;
}

