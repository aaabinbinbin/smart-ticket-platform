package com.smartticket.rag.service;

import org.springframework.stereotype.Service;

/**
 * 检索查询改写服务。
 *
 * <p>当前先实现轻量、可解释的 rewrite：归一化问题表达、补充检索场景前缀、
 * 去掉创建语气词，避免后续 RAG 链路直接吃到噪声文本。</p>
 */
@Service
public class QueryRewriteService {

    /**
     * 为历史搜索改写查询语句。
     */
    public String rewriteForHistorySearch(String queryText) {
        String normalized = normalizeProblemStatement(queryText);
        if (!hasText(normalized)) {
            return normalized;
        }
        return "历史工单 相似问题 处理经验 " + normalized;
    }

    /**
     * 规范化ProblemStatement。
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

    /**
     * 处理文本。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
