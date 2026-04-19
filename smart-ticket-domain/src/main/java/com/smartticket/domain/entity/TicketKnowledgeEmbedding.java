package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单知识切片实体，对应表 {@code ticket_knowledge_embedding}。
 *
 * <p>第一版先把向量以 JSON 文本形式存入 MySQL，后续接入 pgvector 时可迁移为专用向量字段。</p>
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
    /** 切片对应的向量 JSON 文本，第一版用于证明向量化入库链路已打通。 */
    private String embeddingVector;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
