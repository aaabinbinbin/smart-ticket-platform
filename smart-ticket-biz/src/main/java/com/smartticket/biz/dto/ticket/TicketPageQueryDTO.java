package com.smartticket.biz.dto.ticket;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
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
    // 分页编号
    private int pageNo;
    // 分页Size
    private int pageSize;
    // 状态
    private TicketStatusEnum status;
    // 类型
    private TicketTypeEnum type;
    // 分类
    private TicketCategoryEnum category;
    // 优先级
    private TicketPriorityEnum priority;
}

