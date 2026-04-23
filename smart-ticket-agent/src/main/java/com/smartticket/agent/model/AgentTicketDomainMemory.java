package com.smartticket.agent.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTicketDomainMemory {
    private Long ticketId;
    private String latestEventSummary;
    private String riskStatus;
    private String approvalStatus;
    private LocalDateTime updatedAt;
}
