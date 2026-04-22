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
public class TicketTypeProfile {
    private Long id;
    private Long ticketId;
    private String profileSchema;
    private String profileData;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
