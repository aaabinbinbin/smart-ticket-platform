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
public class AgentUserPreferenceMemory {
    private Long userId;
    private String commonTicketType;
    private String commonCategory;
    private String commonPriority;
    private String commonTerms;
    private String responseStyle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
