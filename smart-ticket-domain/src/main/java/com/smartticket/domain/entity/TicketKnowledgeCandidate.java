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
public class TicketKnowledgeCandidate {
    private Long id;
    private Long ticketId;
    private String status;
    private Integer qualityScore;
    private String decision;
    private String reason;
    private String reviewComment;
    private String sensitiveRisk;
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
