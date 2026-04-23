package com.smartticket.rag.service;

import com.smartticket.rag.model.RetrievalHit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 检索结果轻量 rerank 服务。
 *
 * <p>当前先用规则版 rerank：结合原始向量分数、关键词覆盖率和摘要命中度，
 * 提升相似案例展示的稳定性，为后续接入模型 reranker 预留位置。</p>
 */
@Service
public class RetrievalRerankService {
    private static final double FEEDBACK_WEIGHT = 0.05d;

    private final RagFeedbackService ragFeedbackService;

    public RetrievalRerankService(RagFeedbackService ragFeedbackService) {
        this.ragFeedbackService = ragFeedbackService;
    }

    public List<RetrievalHit> rerank(String queryText, List<RetrievalHit> hits, int topK) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Set<String> queryTerms = tokenize(queryText);
        Map<Long, Double> feedbackScores = ragFeedbackService.feedbackScoreByKnowledge();
        return hits.stream()
                .map(hit -> scoreHit(queryTerms, feedbackScores, hit))
                .sorted(Comparator.comparing(RetrievalHit::getScore).reversed())
                .limit(topK)
                .toList();
    }

    private RetrievalHit scoreHit(Set<String> queryTerms, Map<Long, Double> feedbackScores, RetrievalHit hit) {
        double baseScore = hit.getScore() == null ? 0.0d : hit.getScore();
        double coverageBoost = overlapRatio(queryTerms, tokenize(hit.getChunkText())) * 0.15d;
        double summaryBoost = overlapRatio(queryTerms, tokenize(hit.getContentSummary())) * 0.10d;
        double feedbackBoost = feedbackScores.getOrDefault(hit.getKnowledgeId(), 0.0d) * FEEDBACK_WEIGHT;
        return RetrievalHit.builder()
                .knowledgeId(hit.getKnowledgeId())
                .ticketId(hit.getTicketId())
                .embeddingId(hit.getEmbeddingId())
                .chunkIndex(hit.getChunkIndex())
                .chunkType(hit.getChunkType())
                .sourceField(hit.getSourceField())
                .score(baseScore + coverageBoost + summaryBoost + feedbackBoost)
                .contentSummary(hit.getContentSummary())
                .chunkText(hit.getChunkText())
                .whyMatched(appendFeedbackReason(hit.getWhyMatched(), feedbackScores.getOrDefault(hit.getKnowledgeId(), 0.0d)))
                .similarFields(hit.getSimilarFields())
                .differenceFields(hit.getDifferenceFields())
                .build();
    }

    private String appendFeedbackReason(String reason, double feedbackScore) {
        String base = reason == null ? "" : reason;
        if (feedbackScore > 0) {
            return base + " 历史反馈整体偏正向，已轻微提权。";
        }
        if (feedbackScore < 0) {
            return base + " 历史反馈整体偏负向，已降权。";
        }
        return base;
    }

    private double overlapRatio(Set<String> queryTerms, Set<String> targetTerms) {
        if (queryTerms.isEmpty() || targetTerms.isEmpty()) {
            return 0.0d;
        }
        long matched = queryTerms.stream().filter(targetTerms::contains).count();
        return (double) matched / (double) queryTerms.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Set.of();
        }
        return List.of(text.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+", " ")
                        .trim()
                        .split("\\s+"))
                .stream()
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }
}
