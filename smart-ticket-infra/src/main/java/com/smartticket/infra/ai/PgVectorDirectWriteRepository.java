package com.smartticket.infra.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PGvector 直写仓储。
 *
 * <p>与 {@code PgVectorStore.add()} 不同，本类直接使用 JDBC 写入预计算好的向量，
 * 避免 PgVectorStore 内部二次调用 EmbeddingModel 所带来的重复 API 开销和失效风险。</p>
 *
 * <p>只负责写入；查询仍走 {@code PgVectorStore} (Holder)，两者共享同一张表。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "smart-ticket.ai.vector-store", name = "enabled", havingValue = "true")
public class PgVectorDirectWriteRepository {

    private static final Logger log = LoggerFactory.getLogger(PgVectorDirectWriteRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final String schema;
    private final String table;

    /**
     * Document 与预计算向量的配对记录。
     *
     * @param document Spring AI Document（含 id, text, metadata）
     * @param embedding 预计算的浮点向量，维度必须与 PGvector 表定义一致 (1536)
     */
    public record VectorDocument(Document document, List<Float> embedding) {
    }

    public PgVectorDirectWriteRepository(
            @Qualifier("pgvectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            PgVectorStoreProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.schema = properties.getSchemaName() != null ? properties.getSchemaName() : "public";
        this.table = properties.getTableName() != null ? properties.getTableName() : "vector_store";
    }

    /**
     * 批量写入或更新向量文档。
     *
     * <p>使用 {@code INSERT ... ON CONFLICT (id) DO UPDATE} 实现幂等性，
     * 同一文档重复构建时会覆盖内容、元数据和向量。</p>
     */
    public void upsertDocuments(List<VectorDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        String sql = ("INSERT INTO %s.%s (id, content, metadata, embedding) VALUES (?, ?, ?::json, ?::vector) "
                + "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, "
                + "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding")
                .formatted(schema, table);

        int[][] updateCounts = jdbcTemplate.batchUpdate(sql, documents, documents.size(), (ps, vd) -> {
            Document doc = vd.document();
            ps.setObject(1, UUID.fromString(doc.getId()));
            ps.setString(2, doc.getText());
            ps.setString(3, toJsonString(doc.getMetadata()));
            ps.setString(4, toVectorString(vd.embedding()));
        });

        int totalUpdated = 0;
        for (int[] batch : updateCounts) {
            for (int count : batch) {
                totalUpdated += count;
            }
        }
        log.info("PGvector 直写完成：table={}.{}, documents={}, updated={}",
                schema, table, documents.size(), totalUpdated);
    }

    /**
     * 按 knowledgeId 删除切片。
     *
     * <p>metadata 中存储了 knowledgeId，通过 JSON 条件匹配删除旧切片。</p>
     */
    public void deleteByKnowledgeId(Long knowledgeId) {
        String sql = "DELETE FROM %s.%s WHERE metadata ->> 'knowledgeId' = ?".formatted(schema, table);
        int deleted = jdbcTemplate.update(sql, String.valueOf(knowledgeId));
        log.debug("PGvector 删除完成：table={}.{}, knowledgeId={}, deleted={}",
                schema, table, knowledgeId, deleted);
    }

    /** 将 Map 转换为 JSON 字符串。 */
    private static String toJsonString(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /** 将 Float 列表转换为 PGvector 格式字符串，如 [0.123, 0.456, ...]。 */
    private static String toVectorString(List<Float> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return "[]";
        }
        return embedding.stream()
                .map(v -> String.format(Locale.ROOT, "%.6f", v))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
