package com.smartticket.rag.service;

import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.entity.TicketKnowledgeEmbedding;
import com.smartticket.rag.embedding.EmbeddingModelClient;
import com.smartticket.rag.repository.TicketKnowledgeEmbeddingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识向量化服务。
 *
 * <p>该服务负责文本切片、调用 embedding 模型、生成向量并写入 ticket_knowledge_embedding。
 * 它不负责判断工单是否可以沉淀知识，也不做向量检索。</p>
 */
@Service
public class EmbeddingService {
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 80;

    /** Embedding 模型客户端。 */
    private final EmbeddingModelClient embeddingModelClient;

    /** 知识切片仓储。 */
    private final TicketKnowledgeEmbeddingRepository embeddingRepository;

    public EmbeddingService(
            EmbeddingModelClient embeddingModelClient,
            TicketKnowledgeEmbeddingRepository embeddingRepository
    ) {
        this.embeddingModelClient = embeddingModelClient;
        this.embeddingRepository = embeddingRepository;
    }

    /**
     * 对知识正文切片并写入向量记录。
     *
     * <p>同一条知识重新构建时会先删除旧切片，再写入新切片，保证第一版链路具备简单幂等性。</p>
     */
    @Transactional
    public List<TicketKnowledgeEmbedding> embedKnowledge(TicketKnowledge knowledge) {
        if (knowledge == null || knowledge.getId() == null || !hasText(knowledge.getContent())) {
            return List.of();
        }
        embeddingRepository.deleteByKnowledgeId(knowledge.getId());
        List<String> chunks = splitText(knowledge.getContent());
        List<TicketKnowledgeEmbedding> saved = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<Double> vector = embeddingModelClient.embed(chunk);
            TicketKnowledgeEmbedding embedding = TicketKnowledgeEmbedding.builder()
                    .knowledgeId(knowledge.getId())
                    .chunkIndex(i)
                    .chunkText(chunk)
                    .embeddingVector(toJsonArray(vector))
                    .build();
            embeddingRepository.insert(embedding);
            saved.add(embedding);
        }
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
