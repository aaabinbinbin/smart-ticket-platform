package com.smartticket.api.vo.p1;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMemberVO {
    private Long id;
    private Long queueId;
    private Long userId;
    private Boolean enabled;
    private LocalDateTime lastAssignedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
