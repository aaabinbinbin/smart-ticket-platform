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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 *
 * <p>检索策略：默认采用 originalQuery + rewrittenQuery 双路召回，
 * 合并结果后去重，最后统一 rerank。如果 rewrite 被安全规则判定为不安全（safeToUse=false），
 * 则降级为仅使用 originalQuery 单路检索。</p>
 */
@Service
public class RetrievalService {
    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    // K
    private static final int DEFAULT_TOP_K = 3;
    // K
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

    /**
     * 构造检索服务。
     */
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
     * <p>默认双路召回：originalQuery + rewrittenQuery 各自检索，合并结果后去重再 rerank。
     * 如果 rewrite 被安全规则判定为不安全，降级为仅使用 originalQuery。</p>
     *
     * @param request 检索请求
     * @return 结构化检索结果
     */
    public RetrievalResult retrieve(RetrievalRequest request) {
        String queryText = request == null ? null : request.getQueryText();
        int topK = normalizeTopK(request == null ? null : request.getTopK());
        boolean rewriteEnabled = request != null && request.isRewrite();

        // Step 1: rewrite（如果 rewrite 未启用，仅做基本归一化）
        String originalQuery = normalizeQuery(queryText);
        if (!hasText(originalQuery)) {
            return emptyResult(queryText, topK, "EMPTY");
        }

        if (!rewriteEnabled) {
            // rewrite 未启用：单路检索，仅使用 normalized query
            return singlePathRetrieve(queryText, originalQuery, topK);
        }

        // rewrite 已启用：执行双路检索（带安全检查）
        RewriteResult rewriteResult = queryRewriteService.rewriteForHistorySearch(queryText);
        log.debug("RAG rewrite: original='{}', rewritten='{}', safeToUse={}",
                rewriteResult.getOriginalQuery(), rewriteResult.getRewrittenQuery(), rewriteResult.isSafeToUse());

        // Step 2: 确定需要检索的查询列表
        List<String> searchQueries = new ArrayList<>();
        searchQueries.add(rewriteResult.getOriginalQuery());
        if (rewriteResult.isSafeToUse() && hasText(rewriteResult.getRewrittenQuery())
                && !rewriteResult.getRewrittenQuery().equals(rewriteResult.getOriginalQuery())) {
            searchQueries.add(rewriteResult.getRewrittenQuery());
        }

        // Step 3: 对每个查询执行检索，收集所有原始命中
        List<RetrievalHit> allRawHits = new ArrayList<>();
        String retrievalPath = "EMPTY";
        boolean fallbackUsed = false;

        for (String query : searchQueries) {
            Optional<List<RetrievalHit>> vectorHits = searchFromVectorStore(query, topK);
            if (vectorHits.isPresent()) {
                allRawHits.addAll(vectorHits.get());
                retrievalPath = "PGVECTOR";
            } else {
                List<RetrievalHit> mysqlHits = searchFromMysqlFallback(query, topK);
                allRawHits.addAll(mysqlHits);
                if (!mysqlHits.isEmpty()) {
                    retrievalPath = "MYSQL_FALLBACK";
                    fallbackUsed = true;
                }
            }
        }

        // Step 4: 按 knowledgeId 去重
        List<RetrievalHit> deduplicatedHits = deduplicateByKnowledgeId(allRawHits);

        if (deduplicatedHits.isEmpty()) {
            log.warn("RAG 检索无结果，query='{}', dualPath={}", queryText, searchQueries.size() > 1);
            return emptyResult(queryText, topK, retrievalPath, fallbackUsed,
                    rewriteResult.getRewrittenQuery(), rewriteResult.isSafeToUse());
        }

        // Step 5: 统一 rerank
        String rerankQuery = hasText(rewriteResult.getOriginalQuery())
                ? rewriteResult.getOriginalQuery() : queryText;
        List<RetrievalHit> rerankedHits = retrievalRerankService.rerank(rerankQuery, deduplicatedHits, topK);

        log.info("RAG 检索路径={}, fallbackUsed={}, dualPath={}, query='{}', topK={}, rawHits={}, dedupHits={}, finalHits={}",
                retrievalPath, fallbackUsed, searchQueries.size() > 1, queryText, topK,
                allRawHits.size(), deduplicatedHits.size(), rerankedHits.size());

        return RetrievalResult.builder()
                .queryText(queryText)
                .rewrittenQuery(rewriteResult.getRewrittenQuery())
                .topK(topK)
                .retrievalPath(retrievalPath)
                .fallbackUsed(fallbackUsed)
                .dualPathUsed(searchQueries.size() > 1)
                .hits(rerankedHits)
                .build();
    }

    /**
     * rewrite 未启用时的单路检索。
     */
    private RetrievalResult singlePathRetrieve(String queryText, String normalizedQuery, int topK) {
        Optional<RetrievalResult> vectorResult = retrieveFromVectorStore(queryText, normalizedQuery, topK);
        if (vectorResult.isPresent()) {
            return vectorResult.get();
        }
        return retrieveFromMysqlFallback(queryText, normalizedQuery, topK);
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

    // ========== 双路检索：PGvector ==========

    /**
     * 从 PGvector 检索原始命中列表（不经过 rerank，由调用方合并后统一 rerank）。
     */
    private Optional<List<RetrievalHit>> searchFromVectorStore(String query, int topK) {
        if (!vectorStoreEnabled) {
            return Optional.empty();
        }
        SpringAiVectorStoreHolder holder = vectorStoreHolderProvider.getIfAvailable();
        if (holder == null || holder.vectorStore() == null) {
            log.warn("RAG 向量存储未就绪，跳过 PGvector 检索");
            return Optional.empty();
        }
        try {
            List<Document> documents = holder.vectorStore().similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThresholdAll()
                    .build());
            return Optional.of(documents.stream()
                    .map(this::toHit)
                    .toList());
        } catch (RuntimeException ex) {
            log.warn("PGvector 检索失败，跳过：reason={}", ex.getMessage());
            return Optional.empty();
        }
    }

    // ========== 双路检索：MySQL 兜底 ==========

    /**
     * 从 MySQL JSON 向量检索原始命中列表（不经过 rerank，由调用方合并后统一 rerank）。
     */
    private List<RetrievalHit> searchFromMysqlFallback(String query, int topK) {
        if (!hasText(query)) {
            return List.of();
        }
        List<Double> queryVector = embeddingModelClient.embed(query);
        Map<Long, TicketKnowledge> knowledgeMap = activeKnowledgeMap();
        if (knowledgeMap.isEmpty()) {
            log.warn("MySQL 检索无可用知识，query='{}'", query);
            return List.of();
        }
        return embeddingRepository.findByKnowledgeIds(List.copyOf(knowledgeMap.keySet()))
                .stream()
                .filter(embedding -> hasText(embedding.getEmbeddingVector()))
                .map(embedding -> toHit(embedding, knowledgeMap.get(embedding.getKnowledgeId()), queryVector))
                .filter(hit -> hit.getScore() != null)
                .sorted(Comparator.comparing(RetrievalHit::getScore).reversed())
                .toList();
    }

    // ========== 去重 ==========

    /**
     * 按 knowledgeId 去重，保留 score 更高的命中。
     */
    private List<RetrievalHit> deduplicateByKnowledgeId(List<RetrievalHit> hits) {
        Map<Long, RetrievalHit> bestByKnowledgeId = new HashMap<>();
        // 用 LinkedHashMap 保证插入顺序
        Map<Long, RetrievalHit> ordered = new java.util.LinkedHashMap<>();
        for (RetrievalHit hit : hits) {
            Long knowledgeId = hit.getKnowledgeId();
            if (knowledgeId == null) {
                // 没有 knowledgeId 的保留所有
                continue;
            }
            RetrievalHit existing = bestByKnowledgeId.get(knowledgeId);
            if (existing == null) {
                bestByKnowledgeId.put(knowledgeId, hit);
                ordered.put(knowledgeId, hit);
            } else if (hit.getScore() != null && (existing.getScore() == null || hit.getScore() > existing.getScore())) {
                bestByKnowledgeId.put(knowledgeId, hit);
                ordered.put(knowledgeId, hit);
            }
        }
        return List.copyOf(ordered.values());
    }

    // ========== 兼容旧方法（保留 PGvector 全链路，用于外部调用） ==========

    /** 使用 Spring AI VectorStore 执行 PGvector 相似度检索（保留原方法签名）。 */
    Optional<RetrievalResult> retrieveFromVectorStore(String queryText, String rewrittenQuery, int topK) {
        return retrieveFromVectorStore(queryText, rewrittenQuery, topK, true);
    }

    private Optional<RetrievalResult> retrieveFromVectorStore(
            String queryText, String rewrittenQuery, int topK, boolean doRerank) {
        if (!vectorStoreEnabled) {
            return Optional.empty();
        }
        SpringAiVectorStoreHolder holder = vectorStoreHolderProvider.getIfAvailable();
        if (holder == null || holder.vectorStore() == null) {
            log.warn("RAG 检索路径=MYSQL_FALLBACK，fallbackUsed=true，reason=vector-store-not-ready，query='{}'", rewrittenQuery);
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
            List<RetrievalHit> finalHits = doRerank
                    ? retrievalRerankService.rerank(rewrittenQuery, hits, topK)
                    : hits;
            log.info("RAG 检索路径=PGVECTOR，fallbackUsed=false，query='{}', topK={}, hits={}",
                    rewrittenQuery, topK, finalHits.size());
            return Optional.of(RetrievalResult.builder()
                    .queryText(queryText)
                    .rewrittenQuery(rewrittenQuery)
                    .topK(topK)
                    .retrievalPath("PGVECTOR")
                    .fallbackUsed(false)
                    .hits(finalHits)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Spring AI 向量检索失败，回退到 MySQL 向量：reason={}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** 使用 MySQL JSON 向量执行默认兜底检索（保留原方法签名）。 */
    RetrievalResult retrieveFromMysqlFallback(String queryText, String rewrittenQuery, int topK) {
        List<Double> queryVector = embeddingModelClient.embed(rewrittenQuery);
        Map<Long, TicketKnowledge> knowledgeMap = activeKnowledgeMap();
        if (knowledgeMap.isEmpty()) {
            log.warn("RAG 检索路径=MYSQL_FALLBACK，无可用知识记录，query='{}'", rewrittenQuery);
            return RetrievalResult.builder()
                    .queryText(queryText)
                    .rewrittenQuery(rewrittenQuery)
                    .topK(topK)
                    .retrievalPath("MYSQL_FALLBACK")
                    .fallbackUsed(true)
                    .hits(List.of())
                    .build();
        }
        List<RetrievalHit> hits = embeddingRepository.findByKnowledgeIds(List.copyOf(knowledgeMap.keySet()))
                .stream()
                .filter(embedding -> hasText(embedding.getEmbeddingVector()))
                .map(embedding -> toHit(embedding, knowledgeMap.get(embedding.getKnowledgeId()), queryVector))
                .filter(hit -> hit.getScore() != null)
                .sorted(Comparator.comparing(RetrievalHit::getScore).reversed())
                .toList();
        List<RetrievalHit> rerankedHits = retrievalRerankService.rerank(rewrittenQuery, hits, topK);
        log.warn("RAG 检索路径=MYSQL_FALLBACK，fallbackUsed=true，query='{}', topK={}, hits={}",
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

    // ========== 结果构建辅助 ==========

    /** 构造空结果。 */
    private RetrievalResult emptyResult(String queryText, int topK, String path) {
        return RetrievalResult.builder()
                .queryText(queryText)
                .topK(topK)
                .retrievalPath(path)
                .fallbackUsed(false)
                .hits(List.of())
                .build();
    }

    /** 构造带详细信息的空结果。 */
    private RetrievalResult emptyResult(String queryText, int topK, String path,
                                         boolean fallbackUsed, String rewrittenQuery, boolean dualPath) {
        return RetrievalResult.builder()
                .queryText(queryText)
                .rewrittenQuery(rewrittenQuery)
                .topK(topK)
                .retrievalPath(path)
                .fallbackUsed(fallbackUsed)
                .dualPathUsed(dualPath)
                .hits(List.of())
                .build();
    }

    // ========== 原有方法保持不变 ==========

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
    RetrievalHit toHit(Document document) {
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

    /**
     * 处理Reason。
     */
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
