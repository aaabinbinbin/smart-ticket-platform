package com.smartticket.rag.service;

import org.springframework.stereotype.Service;

/**
 * 检索查询改写服务。
 *
 * <p>当前先实现轻量、可解释的 rewrite：归一化问题表达、补充检索场景前缀、
 * 去掉创建语气词，避免后续 RAG 链路直接吃到噪声文本。</p>
 *
 * <p>安全性保证：
 * <ul>
 *   <li>禁止删除否定词（不、不要、不能、无法、拒绝、失败）</li>
 *   <li>禁止删除核心故障词（报错、异常、500、超时、登录失败、权限不足）</li>
 *   <li>改写后长度少于原文 50% 时标记为不安全，不参与检索</li>
 * </ul>
 * </p>
 */
@Service
public class QueryRewriteService {

    // 否定词白名单，rewrite 时不能删除这些词
    private static final String[] NEGATION_WORDS = {
            "不", "不要", "不能", "无法", "拒绝", "失败"
    };

    // 核心故障词白名单，rewrite 时不能删除这些词
    private static final String[] CORE_PROBLEM_WORDS = {
            "报错", "异常", "500", "超时", "登录失败", "权限不足"
    };

    /**
     * 为历史搜索改写查询语句，返回包含安全标记的改写结果。
     *
     * @param queryText 原始查询文本
     * @return 改写结果（含 originalQuery、rewrittenQuery、safeToUse 标记）
     */
    public RewriteResult rewriteForHistorySearch(String queryText) {
        String original = normalizeRaw(queryText);
        if (!hasText(original)) {
            return RewriteResult.unsafe(original, "", "输入文本为空");
        }

        String normalized = normalizeProblemStatement(original);
        String rewritten = hasText(normalized) ? "历史工单 相似问题 处理经验 " + normalized : "";

        // 安全检查
        boolean safeToUse = isSafeRewrite(original, rewritten);

        if (!safeToUse) {
            return RewriteResult.unsafe(original, rewritten, "改写后可能丢失关键信息，仅使用原始查询");
        }

        return RewriteResult.safe(original, rewritten);
    }

    /**
     * 规范化问题表述（去掉创建语气词和引导词）。
     */
    public String normalizeProblemStatement(String queryText) {
        if (!hasText(queryText)) {
            return "";
        }
        String normalized = queryText.trim()
                .replaceAll("\\s+", " ")
                .replace("帮我创建一个", "")
                .replace("帮我创建", "")
                .replace("创建一个", "")
                .replace("创建", "")
                .replace("新建", "")
                .replace("提交", "")
                .replace("发起", "")
                .replace("工单", "")
                .trim();
        normalized = normalized.replaceAll("^(关于|问题是|现象是)", "").trim();
        return normalized;
    }

    // ========== 安全检查 ==========

    /**
     * 检查 rewrite 是否安全。
     *
     * <p>安全规则：</p>
     * <ol>
     *   <li>不能删除否定词</li>
     *   <li>不能删除核心故障词</li>
     *   <li>改写后长度不少于原文 50%</li>
     * </ol>
     */
    boolean isSafeRewrite(String original, String rewritten) {
        if (!hasText(original)) {
            return false;
        }
        if (!hasText(rewritten)) {
            return false;
        }

        // 规则 1：不能删除否定词
        for (String word : NEGATION_WORDS) {
            if (original.contains(word) && !rewritten.contains(word)) {
                return false;
            }
        }

        // 规则 2：不能删除核心故障词
        for (String word : CORE_PROBLEM_WORDS) {
            if (original.contains(word) && !rewritten.contains(word)) {
                return false;
            }
        }

        // 规则 3：改写后长度不少于原文 50%
        if (rewritten.length() < original.length() * 0.5) {
            return false;
        }

        return true;
    }

    // ========== 辅助方法 ==========

    /**
     * 原始文本规范化（仅去空白，不做语义修改）。
     */
    private String normalizeRaw(String queryText) {
        return queryText == null ? "" : queryText.trim().replaceAll("\\s+", " ");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
