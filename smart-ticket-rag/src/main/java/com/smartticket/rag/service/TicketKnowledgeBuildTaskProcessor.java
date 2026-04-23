package com.smartticket.rag.service;

import com.smartticket.biz.service.knowledge.TicketKnowledgeService;
import com.smartticket.domain.entity.TicketKnowledge;
import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.mapper.TicketKnowledgeBuildTaskMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketKnowledgeBuildTaskProcessor {
    private static final Logger log = LoggerFactory.getLogger(TicketKnowledgeBuildTaskProcessor.class);

    private final TicketKnowledgeBuildTaskMapper taskMapper;
    private final TicketKnowledgeService ticketKnowledgeService;
    private final EmbeddingService embeddingService;
    private final KnowledgeAdmissionService knowledgeAdmissionService;
    private final int maxRetries;

    public TicketKnowledgeBuildTaskProcessor(
            TicketKnowledgeBuildTaskMapper taskMapper,
            TicketKnowledgeService ticketKnowledgeService,
            EmbeddingService embeddingService,
            KnowledgeAdmissionService knowledgeAdmissionService,
            @Value("${smart-ticket.knowledge.task.max-retries:5}") int maxRetries
    ) {
        this.taskMapper = taskMapper;
        this.ticketKnowledgeService = ticketKnowledgeService;
        this.embeddingService = embeddingService;
        this.knowledgeAdmissionService = knowledgeAdmissionService;
        this.maxRetries = maxRetries;
    }

    @Transactional
    public boolean process(Long taskId) {
        return process(taskId, false);
    }

    @Transactional
    public boolean forceBuild(Long taskId) {
        return process(taskId, true);
    }

    private boolean process(Long taskId, boolean forceBuild) {
        TicketKnowledgeBuildTask task = taskMapper.findById(taskId);
        if (task == null) {
            log.warn("knowledge build task not found: taskId={}", taskId);
            return false;
        }
        String lockedBy = "knowledge-worker-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.claim(taskId, lockedBy, now) <= 0) {
            log.info("knowledge build task skipped because it is not claimable: taskId={}, status={}", taskId, task.getStatus());
            return false;
        }

        try {
            Long ticketId = task.getTicketId();
            log.info("ticket knowledge task started: taskId={}, ticketId={}", taskId, ticketId);
            KnowledgeAdmissionResult admission = forceBuild ? null : knowledgeAdmissionService.evaluate(ticketId);
            if (admission != null && !admission.autoApproved()) {
                log.info("ticket knowledge task stopped by admission: taskId={}, ticketId={}, decision={}, score={}, reason={}",
                        taskId, ticketId, admission.getDecision(), admission.getQualityScore(), admission.getReason());
                taskMapper.markSuccess(taskId);
                return true;
            }
            Optional<TicketKnowledge> knowledge = ticketKnowledgeService.buildKnowledge(ticketId);
            if (knowledge.isEmpty()) {
                log.info("ticket knowledge task skipped: taskId={}, ticketId={} not eligible", taskId, ticketId);
                taskMapper.markSuccess(taskId);
                return true;
            }
            int chunks = embeddingService.embedKnowledge(knowledge.get()).size();
            taskMapper.markSuccess(taskId);
            log.info("ticket knowledge task finished: taskId={}, ticketId={}, knowledgeId={}, chunks={}",
                    taskId, ticketId, knowledge.get().getId(), chunks);
            return true;
        } catch (RuntimeException ex) {
            TicketKnowledgeBuildTask latest = taskMapper.findById(taskId);
            int retryCount = latest == null || latest.getRetryCount() == null ? 0 : latest.getRetryCount();
            boolean dead = retryCount + 1 >= maxRetries;
            LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(Math.min(60, (long) Math.pow(2, retryCount)));
            taskMapper.markFailed(taskId, limit(ex.getMessage(), 1000), nextRetryAt, dead);
            log.warn("ticket knowledge task failed: taskId={}, dead={}, reason={}", taskId, dead, ex.getMessage(), ex);
            return false;
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
