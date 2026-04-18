package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单知识切片实体，对应表 {@code ticket_knowledge_embedding}。
 *
 * <p>当前 MySQL 表只保存切片文本。真正的向量字段后续接入 pgvector 时再扩展。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketKnowledgeEmbedding {
    /** 知识切片主键。 */
    private Long id;
    /** 所属知识 ID。 */
    private Long knowledgeId;
    /** 切片序号。 */
    private Integer chunkIndex;
    /** 切片文本内容。 */
    private String chunkText;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
