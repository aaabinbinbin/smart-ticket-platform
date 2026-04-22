package com.smartticket.biz.dto.ticket;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketCreateCommandDTO {
    private String title;
    private String description;
    private TicketTypeEnum type;
    private Map<String, Object> typeProfile;
    private TicketCategoryEnum category;
    private TicketPriorityEnum priority;
    private String idempotencyKey;
}

