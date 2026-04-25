package com.smartticket.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG Dashboard 指标 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDashboardDTO {
    /** 知识记录总数。 */
    private long knowledgeCount;
    /** 知识构建成功数。 */
    private long knowledgeBuildSuccessCount;
    /** 知识构建失败数。 */
    private long knowledgeBuildFailedCount;
    /** Embedding 切片总数。 */
    private long embeddingChunkCount;
    /** 最近一次知识构建时间。 */
    private String latestKnowledgeBuildAt;
    /** 当前检索路径。 */
    private String retrievalPath;
}
