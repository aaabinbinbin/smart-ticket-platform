package com.smartticket.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QueryRewriteServiceTest {
    private final QueryRewriteService queryRewriteService = new QueryRewriteService();

    @Test
    void normalizeProblemStatementShouldRemoveCreateNoise() {
        assertEquals("测试环境无法登录", queryRewriteService.normalizeProblemStatement("帮我创建一个 测试环境无法登录 工单"));
    }

    @Test
    void rewriteForHistorySearchShouldAddHistoryPrefix() {
        String rewritten = queryRewriteService.rewriteForHistorySearch("登录失败");

        assertTrue(rewritten.startsWith("历史工单 相似问题 处理经验 "));
        assertTrue(rewritten.contains("登录失败"));
    }
}
