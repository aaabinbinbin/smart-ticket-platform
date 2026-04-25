package com.smartticket.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.mockito.ArgumentMatchers;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
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
        // embed() 会被多次调用（双路召回），使用 anyString() 兼容
        when(embeddingModelClient.embed(anyString())).thenReturn(List.of(1.0d, 0.0d));
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
        // 双路召回标记应为 true
        assertTrue(result.isDualPathUsed());
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
        // 双路召回会调用 embed() 多次，使用 anyString() 兼容
        when(embeddingModelClient.embed(anyString())).thenReturn(List.of(1.0d));
        when(knowledgeRepository.findActive()).thenReturn(List.of());

        RetrievalResult result = retrievalService.checkSimilarCasesBeforeCreate("帮我创建一个测试环境无法登录", "登录时报 500", 3);

        // rewrittenQuery 应包含归一化后的文本，不含"帮我创建"
        assertNotNull(result.getRewrittenQuery());
        assertTrue(result.getRewrittenQuery().contains("测试环境无法登录 登录时报 500")
                || result.getRewrittenQuery().contains("测试环境无法登录"));
        assertFalse(result.getRewrittenQuery().contains("帮我创建一个"));
        assertEquals(0, result.getHits().size());
    }

    @Test
    void retrieveShouldUsePgvectorWhenVectorStoreReady() {
        EmbeddingModelClient embeddingModelClient = mock(EmbeddingModelClient.class);
        TicketKnowledgeEmbeddingRepository embeddingRepository = mock(TicketKnowledgeEmbeddingRepository.class);
        TicketKnowledgeReadRepository knowledgeRepository = mock(TicketKnowledgeReadRepository.class);
        ObjectProvider<SpringAiVectorStoreHolder> vectorStoreProvider = mock(ObjectProvider.class);
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(new SpringAiVectorStoreHolder(vectorStore));
        when(vectorStore.similaritySearch(ArgumentMatchers.any(SearchRequest.class))).thenReturn(List.of(Document.builder()
                .id("00000000-0000-0000-0000-000000000001")
                .text("登录失败处理方案")
                .metadata(Map.of(
                        "knowledgeId", 1L,
                        "ticketId", 1001L,
                        "chunkIndex", 0,
                        "chunkType", "RESOLUTION",
                        "sourceField", "resolutionSteps",
                        "contentSummary", "登录失败处理方案"
                ))
                .score(0.9d)
                .build()));
        RetrievalService retrievalService = new RetrievalService(
                embeddingModelClient,
                embeddingRepository,
                knowledgeRepository,
                new QueryRewriteService(),
                new RetrievalRerankService(feedbackService()),
                vectorStoreProvider,
                true
        );

        // rewrite=false 时走单路检索，不经过 rewrite
        RetrievalResult result = retrievalService.retrieve(RetrievalRequest.builder()
                .queryText("登录失败")
                .topK(3)
                .rewrite(false)
                .build());

        assertEquals("PGVECTOR", result.getRetrievalPath());
        assertFalse(result.isFallbackUsed());
        assertEquals(1, result.getHits().size());
    }

    @Test
    void retrieveShouldFallbackToMysqlWhenVectorStoreEnabledButNotReady() {
        EmbeddingModelClient embeddingModelClient = mock(EmbeddingModelClient.class);
        TicketKnowledgeEmbeddingRepository embeddingRepository = mock(TicketKnowledgeEmbeddingRepository.class);
        TicketKnowledgeReadRepository knowledgeRepository = mock(TicketKnowledgeReadRepository.class);
        ObjectProvider<SpringAiVectorStoreHolder> vectorStoreProvider = mock(ObjectProvider.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(new SpringAiVectorStoreHolder(null));
        when(embeddingModelClient.embed(anyString())).thenReturn(List.of(1.0d));
        when(knowledgeRepository.findActive()).thenReturn(List.of());
        RetrievalService retrievalService = new RetrievalService(
                embeddingModelClient,
                embeddingRepository,
                knowledgeRepository,
                new QueryRewriteService(),
                new RetrievalRerankService(feedbackService()),
                vectorStoreProvider,
                true
        );

        // rewrite=false 时走单路检索
        RetrievalResult result = retrievalService.retrieve(RetrievalRequest.builder()
                .queryText("登录失败")
                .topK(3)
                .rewrite(false)
                .build());

        assertEquals("MYSQL_FALLBACK", result.getRetrievalPath());
        assertTrue(result.isFallbackUsed());
    }

    @Test
    void dualPathRetrieveShouldReturnDedupResults() {
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
        when(embeddingModelClient.embed(anyString())).thenReturn(List.of(1.0d, 0.0d));
        // 两个不同的知识，来自不同工单
        TicketKnowledge knowledge1 = TicketKnowledge.builder().id(1L).ticketId(1001L).contentSummary("登录失败处理").status("ACTIVE").build();
        TicketKnowledge knowledge2 = TicketKnowledge.builder().id(2L).ticketId(1002L).contentSummary("密码重置方案").status("ACTIVE").build();
        when(knowledgeRepository.findActive()).thenReturn(List.of(knowledge1, knowledge2));
        when(embeddingRepository.findByKnowledgeIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        TicketKnowledgeEmbedding.builder().id(10L).knowledgeId(1L).chunkIndex(0).chunkText("登录失败检查账号").embeddingVector("[1.0,0.0]").build(),
                        TicketKnowledgeEmbedding.builder().id(20L).knowledgeId(2L).chunkIndex(0).chunkText("密码重置联系管理员").embeddingVector("[0.0,1.0]").build()
                ));

        RetrievalResult result = retrievalService.retrieve(RetrievalRequest.builder()
                .queryText("登录失败")
                .topK(3)
                .rewrite(true)
                .build());

        assertTrue(result.isDualPathUsed());
        // 双路召回+去重后，应该是 2 个不同的知识
        assertEquals(2, result.getHits().size());
    }

    private RagFeedbackService feedbackService() {
        RagFeedbackMapper mapper = mock(RagFeedbackMapper.class);
        when(mapper.scoreByKnowledge()).thenReturn(List.of());
        return new RagFeedbackService(mapper);
    }
}
