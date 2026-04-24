package com.smartticket.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条历史知识命中结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalHit {
    /** 知识记录 ID。 */
    private Long knowledgeId;

    /** 来源工单 ID。 */
    private Long ticketId;

    /** 命中的切片 ID。 */
    private Long embeddingId;

    /** 命中的切片序号。 */
    private Integer chunkIndex;
    // chunk类型
    private String chunkType;
    // 来源Field
    private String sourceField;

    /** 相似度分数，第一版使用余弦相似度。 */
    private Double score;

    /** 知识摘要，用于展示历史案例结论。 */
    private String contentSummary;

    /** 命中的切片文本，用于解释召回来源。 */
    private String chunkText;
    // whyMatched
    private String whyMatched;
    // similarFields
    private String similarFields;
    // differenceFields
    private String differenceFields;
}
