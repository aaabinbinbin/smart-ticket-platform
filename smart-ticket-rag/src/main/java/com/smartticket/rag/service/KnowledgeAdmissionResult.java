package com.smartticket.rag.service;

import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import lombok.Builder;
import lombok.Data;

/**
 * 知识准入结果类。
 */
@Data
@Builder
public class KnowledgeAdmissionResult {
    // 决策
    private KnowledgeAdmissionDecision decision;
    // qualityScore
    private int qualityScore;
    // reason
    private String reason;
    // 候选
    private TicketKnowledgeCandidate candidate;

    /**
     * 处理Approved。
     */
    public boolean autoApproved() {
        return decision == KnowledgeAdmissionDecision.AUTO_APPROVED;
    }
}
