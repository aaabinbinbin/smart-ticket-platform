package com.smartticket.rag.service;

import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识候选审核结果类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCandidateReviewResult {
    // 候选
    private TicketKnowledgeCandidate candidate;
    // 任务
    private TicketKnowledgeBuildTask task;
    // 构建Triggered
    private boolean buildTriggered;
    // 构建Succeeded
    private boolean buildSucceeded;
}
