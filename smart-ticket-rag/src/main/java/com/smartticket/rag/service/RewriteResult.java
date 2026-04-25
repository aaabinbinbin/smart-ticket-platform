package com.smartticket.rag.service;

/**
 * Query Rewrite 结果。
 *
 * <p>包含原始查询、改写后查询、安全标记等信息。
 * 当 safeToUse=false 时，改写结果不可用于检索，仅使用原始查询。</p>
 */
public class RewriteResult {

    /** 原始查询文本。 */
    private final String originalQuery;

    /** 改写后查询文本（可能为空）。 */
    private final String rewrittenQuery;

    /** 改写是否安全可用。 */
    private final boolean safeToUse;

    /** 不安全原因（仅 safeToUse=false 时有效）。 */
    private final String unsafeReason;

    private RewriteResult(String originalQuery, String rewrittenQuery, boolean safeToUse, String unsafeReason) {
        this.originalQuery = originalQuery;
        this.rewrittenQuery = rewrittenQuery;
        this.safeToUse = safeToUse;
        this.unsafeReason = unsafeReason;
    }

    /**
     * 创建安全的改写结果。
     */
    public static RewriteResult safe(String originalQuery, String rewrittenQuery) {
        return new RewriteResult(originalQuery, rewrittenQuery, true, null);
    }

    /**
     * 创建不安全的改写结果。
     */
    public static RewriteResult unsafe(String originalQuery, String rewrittenQuery, String unsafeReason) {
        return new RewriteResult(originalQuery, rewrittenQuery, false, unsafeReason);
    }

    /**
     * 当 safeToUse=false 时，获取应使用的查询文本（降级到原始查询）。
     */
    public String effectiveQuery() {
        if (safeToUse && hasText(rewrittenQuery)) {
            return rewrittenQuery;
        }
        return originalQuery;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public boolean isSafeToUse() {
        return safeToUse;
    }

    public String getUnsafeReason() {
        return unsafeReason;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
