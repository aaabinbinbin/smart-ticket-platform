package com.smartticket.rag.service;

import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeAdmissionResult {
    private KnowledgeAdmissionDecision decision;
    private int qualityScore;
    private String reason;
    private TicketKnowledgeCandidate candidate;

    public boolean autoApproved() {
        return decision == KnowledgeAdmissionDecision.AUTO_APPROVED;
    }
}
