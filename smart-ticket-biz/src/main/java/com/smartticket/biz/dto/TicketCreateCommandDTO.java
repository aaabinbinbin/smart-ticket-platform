package com.smartticket.biz.dto;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建工单命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreateCommandDTO {
    private String title;
    private String description;
    private TicketCategoryEnum category;
    private TicketPriorityEnum priority;
    private String idempotencyKey;
}
