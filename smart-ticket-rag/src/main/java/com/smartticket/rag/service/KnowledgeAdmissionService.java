package com.smartticket.rag.service;

import com.smartticket.biz.repository.knowledge.TicketKnowledgeCandidateRepository;
import com.smartticket.biz.repository.ticket.TicketCommentRepository;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeAdmissionService {
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketKnowledgeCandidateRepository candidateRepository;

    public KnowledgeAdmissionService(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketKnowledgeCandidateRepository candidateRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.candidateRepository = candidateRepository;
    }

    @Transactional
    public KnowledgeAdmissionResult evaluate(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId);
        List<TicketComment> comments = ticketCommentRepository.findByTicketId(ticketId);
        KnowledgeAdmissionResult result = evaluate(ticket, comments);
        TicketKnowledgeCandidate candidate = TicketKnowledgeCandidate.builder()
                .ticketId(ticketId)
                .status(result.getDecision().name())
                .qualityScore(result.getQualityScore())
                .decision(result.getDecision().name())
                .reason(result.getReason())
                .sensitiveRisk(containsSensitiveInfo(ticket, comments) ? "POSSIBLE" : "NONE")
                .build();
        result.setCandidate(candidateRepository.save(candidate));
        return result;
    }

    public KnowledgeAdmissionResult evaluate(Ticket ticket, List<TicketComment> comments) {
        int score = qualityScore(ticket, comments);
        boolean sensitive = containsSensitiveInfo(ticket, comments);
        KnowledgeAdmissionDecision decision;
        String reason;
        if (ticket == null || ticket.getStatus() != TicketStatusEnum.CLOSED) {
            decision = KnowledgeAdmissionDecision.AUTO_REJECTED;
            reason = "工单未关闭，不能进入知识沉淀。";
        } else if (sensitive) {
            decision = KnowledgeAdmissionDecision.MANUAL_REVIEW;
            reason = "内容可能包含敏感信息，需要人工复核。";
        } else if (score >= 80) {
            decision = KnowledgeAdmissionDecision.AUTO_APPROVED;
            reason = "标题、描述、解决摘要和关键评论较完整，自动通过。";
        } else if (score < 50) {
            decision = KnowledgeAdmissionDecision.AUTO_REJECTED;
            reason = "内容完整度不足，自动拒绝。";
        } else {
            decision = KnowledgeAdmissionDecision.MANUAL_REVIEW;
            reason = "具备一定参考价值，但信息不够完整，需要人工复核。";
        }
        return KnowledgeAdmissionResult.builder()
                .decision(decision)
                .qualityScore(score)
                .reason(reason)
                .build();
    }

    private int qualityScore(Ticket ticket, List<TicketComment> comments) {
        if (ticket == null) {
            return 0;
        }
        int score = 0;
        if (hasText(ticket.getTitle()) && ticket.getTitle().trim().length() >= 6) {
            score += 20;
        }
        if (hasText(ticket.getDescription()) && ticket.getDescription().trim().length() >= 20) {
            score += 25;
        }
        if (hasText(ticket.getSolutionSummary()) && ticket.getSolutionSummary().trim().length() >= 10) {
            score += 35;
        }
        if (comments != null && comments.stream().filter(comment -> hasText(comment.getContent())).count() >= 2) {
            score += 20;
        }
        return Math.min(score, 100);
    }

    private boolean containsSensitiveInfo(Ticket ticket, List<TicketComment> comments) {
        StringBuilder text = new StringBuilder();
        if (ticket != null) {
            text.append(ticket.getTitle()).append(' ')
                    .append(ticket.getDescription()).append(' ')
                    .append(ticket.getSolutionSummary()).append(' ');
        }
        if (comments != null) {
            comments.forEach(comment -> text.append(comment.getContent()).append(' '));
        }
        String normalized = text.toString().toLowerCase();
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("身份证")
                || normalized.contains("手机号")
                || normalized.contains("密码");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
