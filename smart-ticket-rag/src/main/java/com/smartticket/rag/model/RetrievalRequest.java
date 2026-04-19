package com.smartticket.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 历史知识检索请求。
 *
 * <p>第一版只包含查询文本和 TopK，后续可以扩展分类、优先级、租户、权限范围等过滤条件。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalRequest {
    /** 用户输入或业务侧构造的查询文本。 */
    private String queryText;

    /** 期望返回的相似知识数量。 */
    private Integer topK;

    /** 是否启用轻量 query rewrite。 */
    private boolean rewrite;
}
