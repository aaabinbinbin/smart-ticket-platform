package com.smartticket.rag.service;

import com.smartticket.domain.entity.RagFeedback;
import com.smartticket.domain.mapper.RagFeedbackMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * RAG 检索反馈服务，用于记录用户反馈并为后续 rerank 提供统计信号。
 */
@Service
public class RagFeedbackService {
    /**
     * RAG 反馈 Mapper。
     */
    private final RagFeedbackMapper feedbackMapper;

    /**
     * 创建 RAG 反馈服务。
     *
     * @param feedbackMapper RAG 反馈 Mapper
     */
    public RagFeedbackService(RagFeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    /**
     * 提交一次 RAG 检索反馈。
     *
     * @param knowledgeId 命中的知识 ID
     * @param ticketId 来源工单 ID
     * @param sessionId Agent 会话 ID
     * @param queryText 查询文本
     * @param feedbackType 反馈类型
     * @param comment 反馈备注
     * @param createdBy 创建人 ID
     * @return 已保存的反馈记录
     */
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

    /**
     * 查询某条知识关联的全部反馈。
     *
     * @param knowledgeId 知识 ID
     * @return 反馈列表
     */
    public List<RagFeedback> findByKnowledgeId(Long knowledgeId) {
        return feedbackMapper.findByKnowledgeId(knowledgeId);
    }

    /**
     * 查询 RAG 反馈统计信息。
     *
     * @return 反馈统计数据
     */
    public Map<String, Object> stats() {
        return Map.of(
                "knowledgeUsage", feedbackMapper.countByKnowledge(),
                "feedbackTypes", feedbackMapper.countByFeedbackType(),
                "failedQueries", feedbackMapper.countFailedQueries()
        );
    }

    /**
     * 计算每条知识的反馈得分，用于检索结果 rerank。
     *
     * @return 知识 ID 到反馈得分的映射
     */
    public Map<Long, Double> feedbackScoreByKnowledge() {
        return feedbackMapper.scoreByKnowledge().stream()
                .filter(row -> row.get("knowledgeId") != null)
                .collect(Collectors.toMap(
                        row -> ((Number) row.get("knowledgeId")).longValue(),
                        row -> row.get("feedbackScore") == null ? 0.0d : ((Number) row.get("feedbackScore")).doubleValue()
                ));
    }
}
