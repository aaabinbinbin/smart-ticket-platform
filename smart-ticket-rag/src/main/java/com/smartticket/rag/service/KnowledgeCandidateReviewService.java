package com.smartticket.rag.service;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.knowledge.TicketKnowledgeCandidateRepository;
import com.smartticket.biz.service.knowledge.TicketKnowledgeBuildTaskService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeCandidateReviewService {
    private final TicketKnowledgeCandidateRepository candidateRepository;
    private final TicketKnowledgeBuildTaskService taskService;
    private final TicketKnowledgeBuildTaskProcessor taskProcessor;

    public KnowledgeCandidateReviewService(
            TicketKnowledgeCandidateRepository candidateRepository,
            TicketKnowledgeBuildTaskService taskService,
            TicketKnowledgeBuildTaskProcessor taskProcessor
    ) {
        this.candidateRepository = candidateRepository;
        this.taskService = taskService;
        this.taskProcessor = taskProcessor;
    }

    public List<TicketKnowledgeCandidate> list(String status, int limit) {
        return candidateRepository.findByStatus(status == null || status.isBlank() ? "MANUAL_REVIEW" : status, limit);
    }

    public TicketKnowledgeCandidate detail(Long candidateId) {
        return requireCandidate(candidateId);
    }

    @Transactional
    public KnowledgeCandidateReviewResult approve(CurrentUser reviewer, Long candidateId, String comment) {
        requireAdmin(reviewer);
        TicketKnowledgeCandidate candidate = requireCandidate(candidateId);
        candidate.setStatus(KnowledgeCandidateReviewDecision.MANUAL_APPROVED.name());
        candidate.setDecision(KnowledgeCandidateReviewDecision.MANUAL_APPROVED.name());
        candidate.setReviewComment(comment);
        candidate.setReviewedBy(reviewer.getUserId());
        candidate.setReviewedAt(LocalDateTime.now());
        candidateRepository.save(candidate);

        TicketKnowledgeBuildTask task = taskService.createPending(candidate.getTicketId());
        boolean buildSucceeded = task != null && taskProcessor.forceBuild(task.getId());
        return KnowledgeCandidateReviewResult.builder()
                .candidate(candidate)
                .task(task)
                .buildTriggered(task != null)
                .buildSucceeded(buildSucceeded)
                .build();
    }

    @Transactional
    public KnowledgeCandidateReviewResult reject(CurrentUser reviewer, Long candidateId, String comment) {
        requireAdmin(reviewer);
        TicketKnowledgeCandidate candidate = requireCandidate(candidateId);
        candidate.setStatus(KnowledgeCandidateReviewDecision.MANUAL_REJECTED.name());
        candidate.setDecision(KnowledgeCandidateReviewDecision.MANUAL_REJECTED.name());
        candidate.setReviewComment(comment);
        candidate.setReviewedBy(reviewer.getUserId());
        candidate.setReviewedAt(LocalDateTime.now());
        candidateRepository.save(candidate);
        return KnowledgeCandidateReviewResult.builder()
                .candidate(candidate)
                .buildTriggered(false)
                .buildSucceeded(false)
                .build();
    }

    private TicketKnowledgeCandidate requireCandidate(Long candidateId) {
        TicketKnowledgeCandidate candidate = candidateRepository.findById(candidateId);
        if (candidate == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND, "知识候选不存在");
        }
        return candidate;
    }

    private void requireAdmin(CurrentUser reviewer) {
        if (reviewer == null || !reviewer.isAdmin()) {
            throw new BusinessException(BusinessErrorCode.ADMIN_REQUIRED);
        }
    }
}
