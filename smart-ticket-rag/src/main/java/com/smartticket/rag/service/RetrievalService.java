package com.smartticket.rag.service;

import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.entity.TicketKnowledgeEmbedding;
import com.smartticket.rag.embedding.EmbeddingModelClient;
import com.smartticket.rag.model.RetrievalHit;
import com.smartticket.rag.model.RetrievalRequest;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.repository.TicketKnowledgeEmbeddingRepository;
import com.smartticket.rag.repository.TicketKnowledgeReadRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 历史工单 RAG 检索服务。
 *
 * <p>第一版负责接收查询文本、可选轻量 query rewrite、生成查询向量、在已入库知识切片中做 TopK
 * 余弦相似度检索，并返回结构化命中结果。不做 Elasticsearch、不做复杂 rerank。</p>
 */
@Service
public class RetrievalService {
    private static final int DEFAULT_TOP_K = 3;
    private static final int MAX_TOP_K = 10;

    /** Embedding 模型客户端，用于生成查询向量。 */
    private final EmbeddingModelClient embeddingModelClient;

    /** 知识切片仓储，用于读取已向量化的历史知识切片。 */
    private final TicketKnowledgeEmbeddingRepository embeddingRepository;

    /** 知识读取仓储，用于补充知识摘要和来源工单信息。 */
    private final TicketKnowledgeReadRepository knowledgeRepository;

    public RetrievalService(
            EmbeddingModelClient embeddingModelClient,
            TicketKnowledgeEmbeddingRepository embeddingRepository,
            TicketKnowledgeReadRepository knowledgeRepository
    ) {
        this.embeddingModelClient = embeddingModelClient;
        this.embeddingRepository = embeddingRepository;
        this.knowledgeRepository = knowledgeRepository;
    }

    /**
     * 执行历史知识检索。
     *
     * @param request 检索请求
     * @return 结构化检索结果
     */
    public RetrievalResult retrieve(RetrievalRequest request) {
        String queryText = request == null ? null : request.getQueryText();
        int topK = normalizeTopK(request == null ? null : request.getTopK());
        String rewrittenQuery = request != null && request.isRewrite() ? rewriteQuery(queryText) : normalizeQuery(queryText);
        if (!hasText(rewrittenQuery)) {
            return RetrievalResult.builder()
                    .queryText(queryText)
                    .rewrittenQuery(rewrittenQuery)
                    .topK(topK)
                    .hits(List.of())
                    .build();
        }

        List<Double> queryVector = embeddingModelClient.embed(rewrittenQuery);
        Map<Long, TicketKnowledge> knowledgeMap = activeKnowledgeMap();
        List<RetrievalHit> hits = embeddingRepository.findAll()
                .stream()
                .filter(embedding -> embedding.getKnowledgeId() != null)
                .filter(embedding -> knowledgeMap.containsKey(embedding.getKnowledgeId()))
                .filter(embedding -> hasText(embedding.getEmbeddingVector()))
                .map(embedding -> toHit(embedding, knowledgeMap.get(embedding.getKnowledgeId()), queryVector))
                .filter(hit -> hit.getScore() != null)
                .sorted(Comparator.comparing(RetrievalHit::getScore).reversed())
                .limit(topK)
                .toList();

        return RetrievalResult.builder()
                .queryText(queryText)
                .rewrittenQuery(rewrittenQuery)
                .topK(topK)
                .hits(hits)
                .build();
    }

    /**
     * 创建工单前的相似案例检查。
     *
     * <p>该方法只返回参考案例，不阻断创建工单，也不替代业务校验。</p>
     */
    public RetrievalResult checkSimilarCasesBeforeCreate(String title, String description, Integer topK) {
        StringBuilder query = new StringBuilder();
        if (hasText(title)) {
            query.append(title.trim()).append("\n");
        }
        if (hasText(description)) {
            query.append(description.trim());
        }
        return retrieve(RetrievalRequest.builder()
                .queryText(query.toString())
                .topK(topK)
                .rewrite(true)
                .build());
    }

    /** 轻量 query rewrite，第一版只做去噪和场景前缀增强。 */
    private String rewriteQuery(String queryText) {
        String normalized = normalizeQuery(queryText);
        if (!hasText(normalized)) {
            return normalized;
        }
        return "历史工单相似问题 检索: " + normalized;
    }

    /** 归一化查询文本。 */
    private String normalizeQuery(String queryText) {
        return queryText == null ? "" : queryText.trim().replaceAll("\\s+", " ");
    }

    /** 读取可用知识并构造成 ID 映射。 */
    private Map<Long, TicketKnowledge> activeKnowledgeMap() {
        Map<Long, TicketKnowledge> map = new HashMap<>();
        for (TicketKnowledge knowledge : knowledgeRepository.findActive()) {
            map.put(knowledge.getId(), knowledge);
        }
        return map;
    }

    /** 将切片记录转换为检索命中。 */
    private RetrievalHit toHit(
            TicketKnowledgeEmbedding embedding,
            TicketKnowledge knowledge,
            List<Double> queryVector
    ) {
        List<Double> chunkVector = parseVector(embedding.getEmbeddingVector());
        double score = cosineSimilarity(queryVector, chunkVector);
        return RetrievalHit.builder()
                .knowledgeId(knowledge.getId())
                .ticketId(knowledge.getTicketId())
                .embeddingId(embedding.getId())
                .chunkIndex(embedding.getChunkIndex())
                .score(score)
                .contentSummary(knowledge.getContentSummary())
                .chunkText(embedding.getChunkText())
                .build();
    }

    /** 解析第一版 JSON 数组向量文本。 */
    private List<Double> parseVector(String vectorText) {
        if (!hasText(vectorText)) {
            return List.of();
        }
        String normalized = vectorText.trim();
        if (normalized.startsWith("[")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("]")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!hasText(normalized)) {
            return List.of();
        }
        return List.of(normalized.split(","))
                .stream()
                .map(String::trim)
                .filter(this::hasText)
                .map(this::parseDoubleOrZero)
                .toList();
    }

    /** 向量文本解析兜底，避免单个异常值导致整次检索失败。 */
    private Double parseDoubleOrZero(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0.0d;
        }
    }

    /** 计算余弦相似度。 */
    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0d;
        }
        int size = Math.min(left.size(), right.size());
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < size; i++) {
            double l = left.get(i);
            double r = right.get(i);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /** 归一化 TopK，避免一次检索过多数据。 */
    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.min(Math.max(topK, 1), MAX_TOP_K);
    }

    /** 字符串非空判断。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
