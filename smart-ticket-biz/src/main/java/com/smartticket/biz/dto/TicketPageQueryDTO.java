package com.smartticket.biz.dto;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单分页查询条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPageQueryDTO {
    private int pageNo;
    private int pageSize;
    private TicketStatusEnum status;
    private TicketCategoryEnum category;
    private TicketPriorityEnum priority;
}
