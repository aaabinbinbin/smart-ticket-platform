package com.smartticket.rag.service;

import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCandidateReviewResult {
    private TicketKnowledgeCandidate candidate;
    private TicketKnowledgeBuildTask task;
    private boolean buildTriggered;
    private boolean buildSucceeded;
}
