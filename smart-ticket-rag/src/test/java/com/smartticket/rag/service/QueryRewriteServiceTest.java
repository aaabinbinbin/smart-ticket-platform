package com.smartticket.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        RewriteResult result = queryRewriteService.rewriteForHistorySearch("登录失败");

        assertTrue(result.isSafeToUse());
        assertTrue(result.getRewrittenQuery().startsWith("历史工单 相似问题 处理经验 "));
        assertTrue(result.getRewrittenQuery().contains("登录失败"));
    }

    @Test
    void rewriteForHistorySearchShouldPreserveOriginalQuery() {
        RewriteResult result = queryRewriteService.rewriteForHistorySearch("测试环境无法登录");

        assertEquals("测试环境无法登录", result.getOriginalQuery());
        assertTrue(result.getRewrittenQuery().contains("测试环境无法登录"));
    }

    @Test
    void safeRewriteShouldProtectNegationWords() {
        // 否定词 "无法" 不能被删除
        RewriteResult result = queryRewriteService.rewriteForHistorySearch("无法登录系统");

        assertTrue(result.isSafeToUse());
        assertTrue(result.getRewrittenQuery().contains("无法"));
    }

    @Test
    void safeRewriteShouldProtectCoreProblemWords() {
        // "500" 是核心故障词，不能被删除
        RewriteResult result = queryRewriteService.rewriteForHistorySearch("登录报错 500");

        assertTrue(result.isSafeToUse());
        assertTrue(result.getRewrittenQuery().contains("500"));
    }

    @Test
    void emptyInputShouldBeUnsafe() {
        RewriteResult result = queryRewriteService.rewriteForHistorySearch("");

        assertFalse(result.isSafeToUse());
        assertEquals("", result.getOriginalQuery());
    }
}
