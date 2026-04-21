package com.smartticket.biz.dto;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动分派规则分页查询条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentRulePageQueryDTO {
    /** 页码，从 1 开始。 */
    private int pageNo;
    /** 每页大小。 */
    private int pageSize;
    /** 工单分类。 */
    private TicketCategoryEnum category;
    /** 工单优先级。 */
    private TicketPriorityEnum priority;
    /** 是否启用。 */
    private Boolean enabled;
}
