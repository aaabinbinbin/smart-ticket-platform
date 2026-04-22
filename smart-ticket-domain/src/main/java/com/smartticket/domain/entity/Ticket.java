package com.smartticket.domain.entity;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    private Long id;
    private String ticketNo;
    private String title;
    private String description;
    private TicketTypeEnum type;
    private TicketCategoryEnum category;
    private TicketPriorityEnum priority;
    private TicketStatusEnum status;
    private Long creatorId;
    private Long assigneeId;
    private Long groupId;
    private Long queueId;
    private String solutionSummary;
    private String source;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, Object> typeProfile;
}
