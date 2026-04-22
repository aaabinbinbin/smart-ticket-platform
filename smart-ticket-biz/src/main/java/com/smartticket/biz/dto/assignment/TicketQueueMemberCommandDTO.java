package com.smartticket.biz.dto.assignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMemberCommandDTO {
    private Long userId;
    private Boolean enabled;
}

