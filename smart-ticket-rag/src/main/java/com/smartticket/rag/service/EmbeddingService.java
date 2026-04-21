package com.smartticket.rag.service;

import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.entity.TicketKnowledgeEmbedding;
import com.smartticket.infra.ai.VectorStoreConfig.SpringAiVectorStoreHolder;
import com.smartticket.rag.embedding.EmbeddingModelClient;
import com.smartticket.rag.repository.TicketKnowledgeEmbeddingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识向量化服务。
 *
 * <p>该服务负责文本切片、调用 embedding 模型、生成向量并写入检索存储。
 * 当前 P0 同时保留 MySQL 切片表作为默认兜底，并在启用 Spring AI PGvector
 * 后额外写入 VectorStore。</p>
 */
@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 80;
    private static final int DEFAULT_RETRY_TIMES = 2;

    /** Embedding 模型客户端，主实现由 Spring AI EmbeddingModel 适配。 */
    private final EmbeddingModelClient embeddingModelClient;

    /** 知识切片仓储，用于默认 MySQL JSON 向量兜底。 */
    private final TicketKnowledgeEmbeddingRepository embeddingRepository;

    /** Spring AI VectorStore 持有对象，启用 PGvector 后用于向量库写入。 */
    private final ObjectProvider<SpringAiVectorStoreHolder> vectorStoreHolderProvider;

    /** 是否启用 Spring AI VectorStore。 */
    private final boolean vectorStoreEnabled;

    public EmbeddingService(
            EmbeddingModelClient embeddingModelClient,
            TicketKnowledgeEmbeddingRepository embeddingRepository,
            ObjectProvider<SpringAiVectorStoreHolder> vectorStoreHolderProvider,
            @Value("${smart-ticket.ai.vector-store.enabled:false}") boolean vectorStoreEnabled
    ) {
        this.embeddingModelClient = embeddingModelClient;
        this.embeddingRepository = embeddingRepository;
        this.vectorStoreHolderProvider = vectorStoreHolderProvider;
        this.vectorStoreEnabled = vectorStoreEnabled;
    }

    /**
     * 对知识正文切片并写入向量记录。
     *
     * <p>同一条知识重新构建时会先删除旧切片，再写入新切片，保证第一版链路具备简单幂等性。</p>
     *
     * @param knowledge 待向量化的标准知识
     * @return 已写入 MySQL 兜底表的切片记录
     */
    @Transactional
    public List<TicketKnowledgeEmbedding> embedKnowledge(TicketKnowledge knowledge) {
        if (knowledge == null || knowledge.getId() == null || !hasText(knowledge.getContent())) {
            return List.of();
        }
        embeddingRepository.deleteByKnowledgeId(knowledge.getId());
        List<String> chunks = splitText(knowledge.getContent());
        log.info("embedding build started: knowledgeId={}, vectorStoreEnabled={}, chunks={}",
                knowledge.getId(), vectorStoreEnabled, chunks.size());
        List<TicketKnowledgeEmbedding> saved = new ArrayList<>(chunks.size());
        List<Document> vectorDocuments = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<Double> vector = embedWithRetry(knowledge.getId(), i, chunk);
            TicketKnowledgeEmbedding embedding = TicketKnowledgeEmbedding.builder()
                    .knowledgeId(knowledge.getId())
                    .chunkIndex(i)
                    .chunkText(chunk)
                    .embeddingVector(toJsonArray(vector))
                    .build();
            embeddingRepository.insert(embedding);
            saved.add(embedding);
            vectorDocuments.add(toVectorDocument(knowledge, chunk, i));
        }
        writeVectorStore(knowledge.getId(), vectorDocuments);
        log.info("embedding build finished: knowledgeId={}, mysqlChunks={}", knowledge.getId(), saved.size());
        return saved;
    }

    /**
     * 按固定长度切片文本。
     *
     * <p>第一版使用字符窗口和少量重叠，后续可以替换为按段落、标题或 token 的切片策略。</p>
     */
    public List<String> splitText(String text) {
        if (!hasText(text)) {
            return List.of();
        }
        String normalized = text.trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    /**
     * 将知识切片写入 Spring AI VectorStore。
     *
     * <p>VectorStore 由 Spring AI 根据 PGvector 配置负责向量化和入库；MySQL
     * 切片表仍保留为默认开发兜底和可观测记录。</p>
     */
    private void writeVectorStore(Long knowledgeId, List<Document> documents) {
        if (!vectorStoreEnabled || documents == null || documents.isEmpty()) {
            log.info("vector store skipped: knowledgeId={}, reason={}",
                    knowledgeId, vectorStoreEnabled ? "no-documents" : "vector-store-disabled");
            return;
        }
        SpringAiVectorStoreHolder holder = vectorStoreHolderProvider.getIfAvailable();
        if (holder == null || holder.vectorStore() == null) {
            log.warn("vector store unavailable: knowledgeId={}, fallback remains mysql-only", knowledgeId);
            return;
        }
        try {
            holder.vectorStore().delete(documents.stream().map(Document::getId).toList());
            holder.vectorStore().add(documents);
            log.info("vector store write finished: knowledgeId={}, documents={}", knowledgeId, documents.size());
        } catch (RuntimeException ex) {
            log.warn("write spring ai vector store failed, knowledgeId={}, reason={}", knowledgeId, ex.getMessage());
        }
    }

    private List<Double> embedWithRetry(Long knowledgeId, int chunkIndex, String chunk) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= DEFAULT_RETRY_TIMES + 1; attempt++) {
            try {
                return embeddingModelClient.embed(chunk);
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("embedding chunk failed: knowledgeId={}, chunkIndex={}, attempt={}, reason={}",
                        knowledgeId, chunkIndex, attempt, ex.getMessage());
            }
        }
        log.error("embedding chunk permanently failed: knowledgeId={}, chunkIndex={}", knowledgeId, chunkIndex, last);
        throw last;
    }

    /** 将一段知识文本转换为 Spring AI Document，并附加业务元数据。 */
    private Document toVectorDocument(TicketKnowledge knowledge, String chunk, int chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("knowledgeId", knowledge.getId());
        metadata.put("ticketId", knowledge.getTicketId());
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("contentSummary", knowledge.getContentSummary());
        return Document.builder()
                .id(vectorDocumentId(knowledge.getId(), chunkIndex))
                .text(chunk)
                .metadata(metadata)
                .build();
    }

    /** 生成稳定的向量文档 ID，便于同一知识重复构建时覆盖旧切片。 */
    private String vectorDocumentId(Long knowledgeId, int chunkIndex) {
        return "ticket-knowledge-" + knowledgeId + "-" + chunkIndex;
    }

    /** 将向量转换成 JSON 数组文本，便于第一版写入 MySQL TEXT 字段。 */
    private String toJsonArray(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            return "[]";
        }
        return vector.stream()
                .map(value -> String.format(Locale.ROOT, "%.6f", value))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** 字符串非空判断。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
