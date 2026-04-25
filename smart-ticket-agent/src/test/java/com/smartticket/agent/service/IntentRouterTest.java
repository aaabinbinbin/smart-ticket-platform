package com.smartticket.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.router.LlmIntentClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * IntentRouter 关键词路由测试。
 *
 * <p>验证历史工单／类似问题／之前怎么处理等话术能稳定路由到 SEARCH_HISTORY，
 * 不会被误判为 QUERY_TICKET。</p>
 */
@ExtendWith(MockitoExtension.class)
class IntentRouterTest {

    @Mock
    private LlmIntentClassifier llmClassifier;

    private IntentRouter router;

    @BeforeEach
    void setUp() {
        // LLM 返回 null，强制走关键词匹配兜底
        when(llmClassifier.classify(any())).thenReturn(null);
        router = new IntentRouter(llmClassifier);
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsHistoryAndTicket() {
        IntentRoute result = router.route("帮我查一下历史工单，测试环境登录 token 过期之前怎么处理？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsSimilar() {
        IntentRoute result = router.route("有没有类似工单，登录 token expired 是怎么解决的？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsHistoryExperience() {
        IntentRoute result = router.route("参考历史经验，auth-service token 校验失败如何处理？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsKnowledgeBase() {
        IntentRoute result = router.route("查一下知识库，测试环境登录失败并提示 token 过期怎么处理？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsSimilarCases() {
        IntentRoute result = router.route("有没有类似案例，auth-service token 校验失败怎么处理？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsHowToHandle() {
        IntentRoute result = router.route("之前怎么处理登录 token 过期的问题？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsHistoricalCase() {
        IntentRoute result = router.route("查一下历史案例，登录失败怎么排查？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToQueryTicketWhenOnlyQueryTicketKeywords() {
        IntentRoute result = router.route("查一下工单 1001 的详情", null);
        assertEquals(AgentIntent.QUERY_TICKET, result.getIntent());
    }

    @Test
    void shouldRouteToQueryTicketWhenOnlyQueryStatus() {
        IntentRoute result = router.route("查询工单状态", null);
        assertEquals(AgentIntent.QUERY_TICKET, result.getIntent());
    }

    @Test
    void shouldHaveConfidenceScore() {
        IntentRoute result = router.route("有没有类似工单可以参考？", null);
        assertNotNull(result.getConfidence());
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsSimilarAndTicket() {
        IntentRoute result = router.route("有没有类似的工单可以看一下？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsSolutionReference() {
        IntentRoute result = router.route("有没有解决方案参考？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsPreviousSimilarIssue() {
        IntentRoute result = router.route("之前有没有类似问题，支付回调签名失败是怎么处理的？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryWhenQueryContainsSimilarTicketRedis() {
        IntentRoute result = router.route("相似工单里有没有 redis 连接超时的处理方案？", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldRouteToSearchHistoryForEnglishE2eQuery() {
        IntentRoute result = router.route("How to handle login token expired in test environment? Please refer to historical ticket experience.", null);
        assertEquals(AgentIntent.SEARCH_HISTORY, result.getIntent());
    }

    @Test
    void shouldKeepExplicitTicketIdQueryAsQueryTicket() {
        IntentRoute result = router.route("查询 123 号工单的详情", null);
        assertEquals(AgentIntent.QUERY_TICKET, result.getIntent());
    }

    @Test
    void shouldKeepExplicitTicketNumberAsQueryTicket() {
        IntentRoute result = router.route("看一下工单 456 的状态", null);
        assertEquals(AgentIntent.QUERY_TICKET, result.getIntent());
    }
}
