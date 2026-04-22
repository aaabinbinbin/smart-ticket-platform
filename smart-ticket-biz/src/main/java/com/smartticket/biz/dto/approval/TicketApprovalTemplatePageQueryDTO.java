package com.smartticket.biz.dto.approval;

import com.smartticket.domain.enums.TicketTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketApprovalTemplatePageQueryDTO {
    private int pageNo;
    private int pageSize;
    private TicketTypeEnum ticketType;
    private Boolean enabled;
}

