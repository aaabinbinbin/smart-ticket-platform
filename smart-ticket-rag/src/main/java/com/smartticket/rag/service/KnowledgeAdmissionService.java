package com.smartticket.rag.service;

import com.smartticket.biz.repository.knowledge.TicketKnowledgeCandidateRepository;
import com.smartticket.biz.repository.ticket.TicketCommentRepository;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.rag.security.SensitiveInfoDetector;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识准入服务。
 */
@Service
public class KnowledgeAdmissionService {
    // 工单仓储
    private final TicketRepository ticketRepository;
    // 工单评论仓储
    private final TicketCommentRepository ticketCommentRepository;
    // 候选仓储
    private final TicketKnowledgeCandidateRepository candidateRepository;
    // 敏感信息检测器，复用 SensitiveInfoDetector 的正则规则确保准入与脱敏阶段规则一致
    private final SensitiveInfoDetector sensitiveInfoDetector;

    /**
     * 构造知识准入服务。
     */
    public KnowledgeAdmissionService(
            TicketRepository ticketRepository,
            TicketCommentRepository ticketCommentRepository,
            TicketKnowledgeCandidateRepository candidateRepository,
            SensitiveInfoDetector sensitiveInfoDetector
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.candidateRepository = candidateRepository;
        this.sensitiveInfoDetector = sensitiveInfoDetector;
    }

    /**
     * 执行评估。
     */
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

    private static final int AUTO_APPROVE_THRESHOLD = 55;
    private static final int AUTO_REJECT_THRESHOLD = 30;

    /**
     * 执行评估。
     */
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
        } else if (score >= AUTO_APPROVE_THRESHOLD) {
            decision = KnowledgeAdmissionDecision.AUTO_APPROVED;
            reason = "内容完整度、结构质量和工单处理质量均较优，自动通过。";
        } else if (score < AUTO_REJECT_THRESHOLD) {
            decision = KnowledgeAdmissionDecision.AUTO_REJECTED;
            reason = "内容过于简略，缺乏可复用的处理经验，自动拒绝。";
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

    /**
     * 多信号评分模型：内容完整性 + 结构质量 + 工单质量信号。
     *
     * <p>不使用 LLM——所有信号都是结构化数据，规则即可判断。
     * 满分 80 分，纯长度规则不再能拿到满分。</p>
     */
    private int qualityScore(Ticket ticket, List<TicketComment> comments) {
        if (ticket == null) {
            return 0;
        }
        int score = 0;

        // 第1层：内容完整性（最多 45 分，降低长度权重）
        if (hasText(ticket.getTitle()) && ticket.getTitle().trim().length() >= 6) {
            score += 10;
        }
        if (hasText(ticket.getDescription()) && ticket.getDescription().trim().length() >= 20) {
            score += 10;
        }
        if (hasText(ticket.getSolutionSummary()) && ticket.getSolutionSummary().trim().length() >= 10) {
            score += 15;
        }
        long substantiveComments = comments == null ? 0
                : comments.stream().filter(c -> hasText(c.getContent()) && c.getContent().trim().length() >= 10).count();
        if (substantiveComments >= 2) {
            score += 10;
        }

        // 第2层：结构质量（最多 20 分）
        String summary = ticket.getSolutionSummary();
        if (hasText(summary) && summary.trim().length() >= 50) {
            score += 10;  // 有实际解决细节，不是简单一句"已处理"
        }
        if (comments != null && comments.stream().anyMatch(c -> hasText(c.getContent()) && c.getContent().trim().length() > 50)) {
            score += 10;  // 有实质性讨论
        }

        // 第3层：工单质量信号（最多 15 分）
        if (ticket.getAssigneeId() != null) {
            score += 5;  // 有人被分配处理，不是悬空单
        }
        if (hasText(summary) && containsResolutionKeywords(summary)) {
            score += 5;  // 包含实际处理动作的描述
        }
        String combined = (ticket.getTitle() == null ? "" : ticket.getTitle())
                + (ticket.getDescription() == null ? "" : ticket.getDescription());
        if (combined.trim().length() > 50) {
            score += 5;  // 问题描述足够详细
        }

        return Math.min(score, 80);
    }

    private boolean containsResolutionKeywords(String text) {
        String lower = text.toLowerCase();
        return lower.contains("修复") || lower.contains("解决")
                || lower.contains("配置") || lower.contains("重启")
                || lower.contains("调整") || lower.contains("更新")
                || lower.contains("回滚") || lower.contains("迁移")
                || lower.contains("fixed") || lower.contains("resolved")
                || lower.contains("restart") || lower.contains("update");
    }

    /**
     * 使用 SensitiveInfoDetector 检测文本是否包含敏感信息。
     * 复用统一正则规则，确保准入阶段和脱敏阶段规则一致。
     */
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
        return sensitiveInfoDetector.containsSensitiveInfo(text.toString());
    }

    /**
     * 处理文本。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
