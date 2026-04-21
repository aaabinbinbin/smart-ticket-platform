package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMember {
    private Long id;
    private Long queueId;
    private Long userId;
    private Integer enabled;
    private LocalDateTime lastAssignedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
