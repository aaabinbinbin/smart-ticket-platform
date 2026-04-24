package com.smartticket.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartticket.domain.mapper.RagFeedbackMapper;
import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.entity.TicketKnowledgeEmbedding;
import com.smartticket.infra.ai.VectorStoreConfig.SpringAiVectorStoreHolder;
import com.smartticket.rag.embedding.EmbeddingModelClient;
import com.smartticket.rag.model.RetrievalRequest;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.repository.TicketKnowledgeEmbeddingRepository;
import com.smartticket.rag.repository.TicketKnowledgeReadRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RetrievalServiceTest {

    @Test
    void retrieveShouldUseMysqlFallbackWhenVectorStoreDisabled() {
        EmbeddingModelClient embeddingModelClient = mock(EmbeddingModelClient.class);
        TicketKnowledgeEmbeddingRepository embeddingRepository = mock(TicketKnowledgeEmbeddingRepository.class);
        TicketKnowledgeReadRepository knowledgeRepository = mock(TicketKnowledgeReadRepository.class);
        QueryRewriteService queryRewriteService = new QueryRewriteService();
        RetrievalRerankService rerankService = new RetrievalRerankService(feedbackService());
        ObjectProvider<SpringAiVectorStoreHolder> vectorStoreProvider = mock(ObjectProvider.class);
        RetrievalService retrievalService = new RetrievalService(
                embeddingModelClient,
                embeddingRepository,
                knowledgeRepository,
                queryRewriteService,
                rerankService,
                vectorStoreProvider,
                false
        );
        when(embeddingModelClient.embed("历史工单 相似问题 处理经验 登录失败")).thenReturn(List.of(1.0d, 0.0d));
        TicketKnowledge activeKnowledge = TicketKnowledge.builder()
                .id(1L)
                .ticketId(1001L)
                .contentSummary("登录失败处理方案")
                .status("ACTIVE")
                .build();
        when(knowledgeRepository.findActive()).thenReturn(List.of(activeKnowledge));
        when(embeddingRepository.findByKnowledgeIds(List.of(1L))).thenReturn(List.of(TicketKnowledgeEmbedding.builder()
                .id(10L)
                .knowledgeId(1L)
                .chunkIndex(0)
                .chunkText("登录失败建议检查账号锁定")
                .embeddingVector("[1.0,0.0]")
                .build()));

        RetrievalResult result = retrievalService.retrieve(RetrievalRequest.builder()
                .queryText("登录失败")
                .topK(3)
                .rewrite(true)
                .build());

        assertEquals("MYSQL_FALLBACK", result.getRetrievalPath());
        assertTrue(result.isFallbackUsed());
        assertEquals(1, result.getHits().size());
    }

    @Test
    void checkSimilarCasesBeforeCreateShouldNormalizeProblemExpression() {
        EmbeddingModelClient embeddingModelClient = mock(EmbeddingModelClient.class);
        TicketKnowledgeEmbeddingRepository embeddingRepository = mock(TicketKnowledgeEmbeddingRepository.class);
        TicketKnowledgeReadRepository knowledgeRepository = mock(TicketKnowledgeReadRepository.class);
        ObjectProvider<SpringAiVectorStoreHolder> vectorStoreProvider = mock(ObjectProvider.class);
        RetrievalService retrievalService = new RetrievalService(
                embeddingModelClient,
                embeddingRepository,
                knowledgeRepository,
                new QueryRewriteService(),
                new RetrievalRerankService(feedbackService()),
                vectorStoreProvider,
                false
        );
        when(embeddingModelClient.embed("历史工单 相似问题 处理经验 测试环境无法登录 登录时报 500")).thenReturn(List.of(1.0d));
        when(knowledgeRepository.findActive()).thenReturn(List.of());

        RetrievalResult result = retrievalService.checkSimilarCasesBeforeCreate("帮我创建一个测试环境无法登录", "登录时报 500", 3);

        assertTrue(result.getRewrittenQuery().contains("测试环境无法登录 登录时报 500"));
        assertFalse(result.getRewrittenQuery().contains("帮我创建一个"));
        assertEquals(0, result.getHits().size());
    }

    private RagFeedbackService feedbackService() {
        RagFeedbackMapper mapper = mock(RagFeedbackMapper.class);
        when(mapper.scoreByKnowledge()).thenReturn(List.of());
        return new RagFeedbackService(mapper);
    }
}
