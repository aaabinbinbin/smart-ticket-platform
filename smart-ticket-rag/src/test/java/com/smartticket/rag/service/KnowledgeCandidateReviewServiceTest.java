package com.smartticket.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.knowledge.TicketKnowledgeCandidateRepository;
import com.smartticket.biz.service.knowledge.TicketKnowledgeBuildTaskService;
import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeCandidateReviewServiceTest {

    @Test
    void approveShouldUpdateCandidateAndForceBuildTask() {
        TicketKnowledgeCandidateRepository candidateRepository = mock(TicketKnowledgeCandidateRepository.class);
        TicketKnowledgeBuildTaskService taskService = mock(TicketKnowledgeBuildTaskService.class);
        TicketKnowledgeBuildTaskProcessor taskProcessor = mock(TicketKnowledgeBuildTaskProcessor.class);
        KnowledgeCandidateReviewService service = new KnowledgeCandidateReviewService(candidateRepository, taskService, taskProcessor);
        TicketKnowledgeCandidate candidate = TicketKnowledgeCandidate.builder()
                .id(1L)
                .ticketId(1001L)
                .status("MANUAL_REVIEW")
                .decision("MANUAL_REVIEW")
                .build();
        TicketKnowledgeBuildTask task = TicketKnowledgeBuildTask.builder().id(10L).ticketId(1001L).build();
        when(candidateRepository.findById(1L)).thenReturn(candidate);
        when(candidateRepository.save(candidate)).thenReturn(candidate);
        when(taskService.createPending(1001L)).thenReturn(task);
        when(taskProcessor.forceBuild(10L)).thenReturn(true);

        KnowledgeCandidateReviewResult result = service.approve(admin(), 1L, "可以沉淀");

        assertEquals(KnowledgeCandidateReviewDecision.MANUAL_APPROVED.name(), result.getCandidate().getStatus());
        assertTrue(result.isBuildTriggered());
        assertTrue(result.isBuildSucceeded());
        verify(candidateRepository).save(candidate);
        verify(taskProcessor).forceBuild(10L);
    }

    @Test
    void rejectShouldUpdateCandidateWithoutBuild() {
        TicketKnowledgeCandidateRepository candidateRepository = mock(TicketKnowledgeCandidateRepository.class);
        TicketKnowledgeBuildTaskService taskService = mock(TicketKnowledgeBuildTaskService.class);
        TicketKnowledgeBuildTaskProcessor taskProcessor = mock(TicketKnowledgeBuildTaskProcessor.class);
        KnowledgeCandidateReviewService service = new KnowledgeCandidateReviewService(candidateRepository, taskService, taskProcessor);
        TicketKnowledgeCandidate candidate = TicketKnowledgeCandidate.builder()
                .id(1L)
                .ticketId(1001L)
                .status("MANUAL_REVIEW")
                .decision("MANUAL_REVIEW")
                .build();
        when(candidateRepository.findById(1L)).thenReturn(candidate);
        when(candidateRepository.save(candidate)).thenReturn(candidate);

        KnowledgeCandidateReviewResult result = service.reject(admin(), 1L, "信息不完整");

        assertEquals(KnowledgeCandidateReviewDecision.MANUAL_REJECTED.name(), result.getCandidate().getStatus());
        assertEquals(false, result.isBuildTriggered());
        verify(candidateRepository).save(candidate);
    }

    private CurrentUser admin() {
        return CurrentUser.builder()
                .userId(9L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .build();
    }
}
