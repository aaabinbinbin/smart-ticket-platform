package com.smartticket.rag.service;

import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.entity.TicketKnowledgeEmbedding;
import com.smartticket.infra.ai.VectorStoreConfig.SpringAiVectorStoreHolder;
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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 历史工单 RAG 检索服务。
 *
 * <p>启用 PGvector 时优先使用 Spring AI VectorStore 做相似度检索；默认本地开发仍使用
 * MySQL JSON 向量 + 内存 TopK 兜底，避免没有 PostgreSQL/模型密钥时影响 P0 闭环。</p>
 */
@Service
public class RetrievalService {
    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final int DEFAULT_TOP_K = 3;
    private static final int MAX_TOP_K = 10;

    /** Embedding 模型客户端，用于默认 MySQL 兜底检索生成查询向量。 */
    private final EmbeddingModelClient embeddingModelClient;

    /** 知识切片仓储，用于默认 MySQL 兜底检索。 */
    private final TicketKnowledgeEmbeddingRepository embeddingRepository;

    /** 知识读取仓储，用于补充摘要和来源工单信息。 */
    private final TicketKnowledgeReadRepository knowledgeRepository;

    /** 轻量 query rewrite 服务。 */
    private final QueryRewriteService queryRewriteService;

    /** 轻量 rerank 服务。 */
    private final RetrievalRerankService retrievalRerankService;

    /** Spring AI VectorStore 持有对象，启用 PGvector 后用于相似度检索。 */
    private final ObjectProvider<SpringAiVectorStoreHolder> vectorStoreHolderProvider;

    /** 是否启用 Spring AI VectorStore 检索。 */
    private final boolean vectorStoreEnabled;

    public RetrievalService(
            EmbeddingModelClient embeddingModelClient,
            TicketKnowledgeEmbeddingRepository embeddingRepository,
            TicketKnowledgeReadRepository knowledgeRepository,
            QueryRewriteService queryRewriteService,
            RetrievalRerankService retrievalRerankService,
            ObjectProvider<SpringAiVectorStoreHolder> vectorStoreHolderProvider,
            @Value("${smart-ticket.ai.vector-store.enabled:false}") boolean vectorStoreEnabled
    ) {
        this.embeddingModelClient = embeddingModelClient;
        this.embeddingRepository = embeddingRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.queryRewriteService = queryRewriteService;
        this.retrievalRerankService = retrievalRerankService;
        this.vectorStoreHolderProvider = vectorStoreHolderProvider;
        this.vectorStoreEnabled = vectorStoreEnabled;
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
        String rewrittenQuery = request != null && request.isRewrite()
                ? queryRewriteService.rewriteForHistorySearch(queryText)
                : normalizeQuery(queryText);
        if (!hasText(rewrittenQuery)) {
            return RetrievalResult.builder()
                    .queryText(queryText)
                    .rewrittenQuery(rewrittenQuery)
                    .topK(topK)
                    .retrievalPath("EMPTY")
                    .fallbackUsed(false)
                    .hits(List.of())
                    .build();
        }

        Optional<RetrievalResult> vectorStoreResult = retrieveFromVectorStore(queryText, rewrittenQuery, topK);
        if (vectorStoreResult.isPresent()) {
            return vectorStoreResult.get();
        }
        return retrieveFromMysqlFallback(queryText, rewrittenQuery, topK);
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

    /** 使用 Spring AI VectorStore 执行 PGvector 相似度检索。 */
    private Optional<RetrievalResult> retrieveFromVectorStore(String queryText, String rewrittenQuery, int topK) {
        if (!vectorStoreEnabled) {
            return Optional.empty();
        }
        SpringAiVectorStoreHolder holder = vectorStoreHolderProvider.getIfAvailable();
        if (holder == null || holder.vectorStore() == null) {
            return Optional.empty();
        }
        try {
            List<Document> documents = holder.vectorStore().similaritySearch(SearchRequest.builder()
                    .query(rewrittenQuery)
                    .topK(topK)
                    .similarityThresholdAll()
                    .build());
            List<RetrievalHit> hits = documents.stream()
                    .map(this::toHit)
                    .toList();
            List<RetrievalHit> rerankedHits = retrievalRerankService.rerank(rewrittenQuery, hits, topK);
            log.info("rag retrieval path=PGVECTOR, fallbackUsed=false, query='{}', topK={}, hits={}",
                    rewrittenQuery, topK, rerankedHits.size());
            return Optional.of(RetrievalResult.builder()
                    .queryText(queryText)
                    .rewrittenQuery(rewrittenQuery)
                    .topK(topK)
                    .retrievalPath("PGVECTOR")
                    .fallbackUsed(false)
                    .hits(rerankedHits)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("spring ai vector store retrieval failed, fallback to mysql vectors: reason={}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** 使用 MySQL JSON 向量执行默认兜底检索。 */
    private RetrievalResult retrieveFromMysqlFallback(String queryText, String rewrittenQuery, int topK) {
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
                .toList();
        List<RetrievalHit> rerankedHits = retrievalRerankService.rerank(rewrittenQuery, hits, topK);
        log.warn("rag retrieval path=MYSQL_FALLBACK, fallbackUsed=true, query='{}', topK={}, hits={}",
                rewrittenQuery, topK, rerankedHits.size());

        return RetrievalResult.builder()
                .queryText(queryText)
                .rewrittenQuery(rewrittenQuery)
                .topK(topK)
                .retrievalPath("MYSQL_FALLBACK")
                .fallbackUsed(true)
                .hits(rerankedHits)
                .build();
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

    /** 将 Spring AI Document 转换为检索命中。 */
    private RetrievalHit toHit(Document document) {
        Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
        return RetrievalHit.builder()
                .knowledgeId(toLong(metadata.get("knowledgeId")))
                .ticketId(toLong(metadata.get("ticketId")))
                .chunkIndex(toInteger(metadata.get("chunkIndex")))
                .chunkType(asString(metadata.get("chunkType")))
                .sourceField(asString(metadata.get("sourceField")))
                .score(document.getScore())
                .contentSummary(asString(metadata.get("contentSummary")))
                .chunkText(document.getText())
                .whyMatched(matchReason(asString(metadata.get("chunkType")), asString(metadata.get("sourceField"))))
                .similarFields(asString(metadata.get("sourceField")))
                .differenceFields("需结合当前工单事实核对环境、版本、账号和影响范围。")
                .build();
    }

    /** 将 MySQL 切片记录转换为检索命中。 */
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
                .chunkType(embedding.getChunkType())
                .sourceField(embedding.getSourceField())
                .score(score)
                .contentSummary(knowledge.getContentSummary())
                .chunkText(embedding.getChunkText())
                .whyMatched(matchReason(embedding.getChunkType(), embedding.getSourceField()))
                .similarFields(embedding.getSourceField())
                .differenceFields("需结合当前工单事实核对环境、版本、账号和影响范围。")
                .build();
    }

    private String matchReason(String chunkType, String sourceField) {
        if (!hasText(chunkType)) {
            return "命中历史工单全文片段。";
        }
        return switch (chunkType) {
            case "SYMPTOM" -> "命中问题现象摘要，适合判断当前问题是否相似。";
            case "ROOT_CAUSE" -> "命中根因摘要，可作为排查方向参考。";
            case "RESOLUTION" -> "命中处理步骤摘要，可复用为解决方案参考。";
            case "RISK_NOTE" -> "命中风险注意事项，执行前需要重点核对。";
            case "APPLICABLE_SCOPE" -> "命中适用范围，需确认当前工单是否满足这些条件。";
            default -> "命中历史工单全文片段，来源字段：" + (sourceField == null ? "content" : sourceField) + "。";
        };
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

    /** 将元数据值转换为 Long。 */
    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    /** 将元数据值转换为 Integer。 */
    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    /** 将元数据值转换为字符串。 */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 字符串非空判断。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
