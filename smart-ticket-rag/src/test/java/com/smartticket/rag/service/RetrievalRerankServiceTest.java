package com.smartticket.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartticket.domain.mapper.RagFeedbackMapper;
import com.smartticket.rag.model.RetrievalHit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievalRerankServiceTest {

    @Test
    void rerankShouldBoostHelpfulKnowledgeAndPenalizeBadFeedback() {
        RagFeedbackMapper mapper = mock(RagFeedbackMapper.class);
        when(mapper.scoreByKnowledge()).thenReturn(List.of(
                Map.of("knowledgeId", 1L, "feedbackScore", 3.0d),
                Map.of("knowledgeId", 2L, "feedbackScore", -2.0d)
        ));
        RetrievalRerankService service = new RetrievalRerankService(new RagFeedbackService(mapper));

        List<RetrievalHit> hits = service.rerank("登录失败", List.of(
                RetrievalHit.builder()
                        .knowledgeId(2L)
                        .score(0.90d)
                        .chunkText("登录失败")
                        .contentSummary("登录失败")
                        .whyMatched("命中问题现象。")
                        .build(),
                RetrievalHit.builder()
                        .knowledgeId(1L)
                        .score(0.80d)
                        .chunkText("登录失败")
                        .contentSummary("登录失败")
                        .whyMatched("命中问题现象。")
                        .build()
        ), 2);

        assertEquals(1L, hits.get(0).getKnowledgeId());
        assertTrue(hits.get(0).getWhyMatched().contains("正向"));
        assertTrue(hits.get(1).getWhyMatched().contains("负向"));
    }
}
