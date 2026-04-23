package com.smartticket.rag.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.service.knowledge.TicketKnowledgeService;
import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.mapper.TicketKnowledgeBuildTaskMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TicketKnowledgeBuildTaskProcessorTest {

    @Test
    void processShouldBuildKnowledgeAndMarkSuccessWhenAdmissionApproved() {
        TicketKnowledgeBuildTaskMapper taskMapper = mock(TicketKnowledgeBuildTaskMapper.class);
        TicketKnowledgeService knowledgeService = mock(TicketKnowledgeService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        KnowledgeAdmissionService admissionService = mock(KnowledgeAdmissionService.class);
        TicketKnowledgeBuildTaskProcessor processor = new TicketKnowledgeBuildTaskProcessor(
                taskMapper,
                knowledgeService,
                embeddingService,
                admissionService,
                5
        );
        TicketKnowledgeBuildTask task = TicketKnowledgeBuildTask.builder()
                .id(10L)
                .ticketId(1001L)
                .status("PENDING")
                .retryCount(0)
                .build();
        TicketKnowledge knowledge = TicketKnowledge.builder().id(20L).ticketId(1001L).build();
        when(taskMapper.findById(10L)).thenReturn(task);
        when(taskMapper.claim(eq(10L), any(), any())).thenReturn(1);
        when(admissionService.evaluate(1001L)).thenReturn(KnowledgeAdmissionResult.builder()
                .decision(KnowledgeAdmissionDecision.AUTO_APPROVED)
                .qualityScore(95)
                .reason("approved")
                .build());
        when(knowledgeService.buildKnowledge(1001L)).thenReturn(Optional.of(knowledge));
        when(embeddingService.embedKnowledge(knowledge)).thenReturn(List.of());

        processor.process(10L);

        verify(knowledgeService).buildKnowledge(1001L);
        verify(embeddingService).embedKnowledge(knowledge);
        verify(taskMapper).markSuccess(10L);
    }
}
