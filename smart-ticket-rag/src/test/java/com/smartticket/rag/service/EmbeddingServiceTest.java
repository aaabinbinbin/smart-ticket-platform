package com.smartticket.rag.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.infra.ai.VectorStoreConfig.SpringAiVectorStoreHolder;
import com.smartticket.rag.embedding.EmbeddingModelClient;
import com.smartticket.rag.repository.TicketKnowledgeEmbeddingRepository;
import com.smartticket.rag.security.SensitiveInfoDetector;
import com.smartticket.rag.security.SensitiveInfoMasker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class EmbeddingServiceTest {

    @Test
    void embedKnowledgeShouldCreateStructuredChunksBeforeFullTextChunks() {
        EmbeddingModelClient embeddingModelClient = mock(EmbeddingModelClient.class);
        TicketKnowledgeEmbeddingRepository repository = mock(TicketKnowledgeEmbeddingRepository.class);
        ObjectProvider<SpringAiVectorStoreHolder> vectorStoreProvider = mock(ObjectProvider.class);
        when(embeddingModelClient.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of(1.0d, 0.0d));
        EmbeddingService service = new EmbeddingService(
                embeddingModelClient,
                repository,
                vectorStoreProvider,
                new SensitiveInfoMasker(new SensitiveInfoDetector()),
                false
        );

        service.embedKnowledge(TicketKnowledge.builder()
                .id(1L)
                .ticketId(1001L)
                .content("完整知识正文")
                .contentSummary("摘要")
                .symptomSummary("登录返回 500")
                .rootCauseSummary("认证缓存异常")
                .resolutionSteps("清理缓存并重启认证服务")
                .riskNotes("先确认影响范围")
                .applicableScope("适用于登录异常")
                .build());

        verify(repository).deleteByKnowledgeId(1L);
        verify(repository, atLeastOnce()).insert(argThat(embedding -> "SYMPTOM".equals(embedding.getChunkType())));
        verify(repository, atLeastOnce()).insert(argThat(embedding -> "ROOT_CAUSE".equals(embedding.getChunkType())));
        verify(repository, atLeastOnce()).insert(argThat(embedding -> "RESOLUTION".equals(embedding.getChunkType())));
        verify(repository, atLeastOnce()).insert(argThat(embedding -> "RISK_NOTE".equals(embedding.getChunkType())));
        verify(repository, atLeastOnce()).insert(argThat(embedding -> "APPLICABLE_SCOPE".equals(embedding.getChunkType())));
        verify(repository, atLeastOnce()).insert(argThat(embedding -> "FULL_TEXT".equals(embedding.getChunkType())));
    }

    @Test
    void embedKnowledgeShouldMaskSensitiveInfoBeforePersistAndEmbedding() {
        EmbeddingModelClient embeddingModelClient = mock(EmbeddingModelClient.class);
        TicketKnowledgeEmbeddingRepository repository = mock(TicketKnowledgeEmbeddingRepository.class);
        ObjectProvider<SpringAiVectorStoreHolder> vectorStoreProvider = mock(ObjectProvider.class);
        when(embeddingModelClient.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of(1.0d, 0.0d));
        EmbeddingService service = new EmbeddingService(
                embeddingModelClient,
                repository,
                vectorStoreProvider,
                new SensitiveInfoMasker(new SensitiveInfoDetector()),
                false
        );

        service.embedKnowledge(TicketKnowledge.builder()
                .id(2L)
                .ticketId(1002L)
                .content("用户 13812345678 邮箱 user@example.com password=abc123 Bearer eyJhbGci.eyJzdWI.sig")
                .contentSummary("摘要")
                .build());

        verify(embeddingModelClient, atLeastOnce()).embed(argThat(text ->
                text.contains("[PHONE_MASKED]")
                        && text.contains("[EMAIL_MASKED]")
                        && text.contains("[PASSWORD_MASKED]")
                        && text.contains("[BEARER_TOKEN_MASKED]")
                        && !text.contains("13812345678")
                        && !text.contains("user@example.com")
                        && !text.contains("abc123")
        ));
        verify(repository, atLeastOnce()).insert(argThat(embedding ->
                embedding.getChunkText().contains("[PHONE_MASKED]")
                        && embedding.getChunkText().contains("[EMAIL_MASKED]")
                        && !embedding.getChunkText().contains("13812345678")
        ));
    }
}
