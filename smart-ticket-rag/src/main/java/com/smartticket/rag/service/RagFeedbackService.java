package com.smartticket.rag.service;

import com.smartticket.domain.entity.RagFeedback;
import com.smartticket.domain.mapper.RagFeedbackMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RagFeedbackService {
    private final RagFeedbackMapper feedbackMapper;

    public RagFeedbackService(RagFeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public RagFeedback submit(
            Long knowledgeId,
            Long ticketId,
            String sessionId,
            String queryText,
            RagFeedbackType feedbackType,
            String comment,
            Long createdBy
    ) {
        RagFeedback feedback = RagFeedback.builder()
                .knowledgeId(knowledgeId)
                .ticketId(ticketId)
                .sessionId(sessionId)
                .queryText(queryText)
                .feedbackType(feedbackType.name())
                .comment(comment)
                .createdBy(createdBy)
                .build();
        feedbackMapper.insert(feedback);
        return feedback;
    }

    public List<RagFeedback> findByKnowledgeId(Long knowledgeId) {
        return feedbackMapper.findByKnowledgeId(knowledgeId);
    }

    public Map<String, Object> stats() {
        return Map.of(
                "knowledgeUsage", feedbackMapper.countByKnowledge(),
                "feedbackTypes", feedbackMapper.countByFeedbackType(),
                "failedQueries", feedbackMapper.countFailedQueries()
        );
    }

    public Map<Long, Double> feedbackScoreByKnowledge() {
        return feedbackMapper.scoreByKnowledge().stream()
                .filter(row -> row.get("knowledgeId") != null)
                .collect(Collectors.toMap(
                        row -> ((Number) row.get("knowledgeId")).longValue(),
                        row -> row.get("feedbackScore") == null ? 0.0d : ((Number) row.get("feedbackScore")).doubleValue()
                ));
    }
}
