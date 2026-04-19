package com.smartticket.rag.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 历史知识检索结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {
    /** 原始查询文本。 */
    private String queryText;

    /** 轻量改写后的查询文本。 */
    private String rewrittenQuery;

    /** 实际使用的 TopK。 */
    private Integer topK;

    /** 命中的历史知识列表。 */
    @Builder.Default
    private List<RetrievalHit> hits = new ArrayList<>();
}
