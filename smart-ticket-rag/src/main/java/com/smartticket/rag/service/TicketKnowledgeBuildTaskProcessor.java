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

/**
 * 工单知识构建任务处理器，负责把数据库 task 转换为正式知识和向量切片。
 */
@Service
public class TicketKnowledgeBuildTaskProcessor {
    private static final Logger log = LoggerFactory.getLogger(TicketKnowledgeBuildTaskProcessor.class);

    /**
     * 知识构建任务 Mapper，用于抢占、更新状态和记录失败。
     */
    private final TicketKnowledgeBuildTaskMapper taskMapper;

    /**
     * 工单知识服务，负责根据工单内容生成正式知识。
     */
    private final TicketKnowledgeService ticketKnowledgeService;

    /**
     * Embedding 服务，负责把正式知识切片并写入检索存储。
     */
    private final EmbeddingService embeddingService;

    /**
     * 知识准入服务，用于判断工单是否能自动入库或需要人工审核。
     */
    private final KnowledgeAdmissionService knowledgeAdmissionService;

    /**
     * 单个任务最大重试次数。
     */
    private final int maxRetries;

    /**
     * 创建知识构建任务处理器。
     *
     * @param taskMapper 知识构建任务 Mapper
     * @param ticketKnowledgeService 工单知识服务
     * @param embeddingService Embedding 服务
     * @param knowledgeAdmissionService 知识准入服务
     * @param maxRetries 最大重试次数
     */
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

    /**
     * 按正常准入流程处理知识构建任务。
     *
     * @param taskId 任务 ID
     * @return 是否处理成功
     */
    @Transactional
    public boolean process(Long taskId) {
        return process(taskId, false);
    }

    /**
     * 跳过自动准入判断并强制构建知识，供人工审核通过后调用。
     *
     * @param taskId 任务 ID
     * @return 是否处理成功
     */
    @Transactional
    public boolean forceBuild(Long taskId) {
        return process(taskId, true);
    }

    /**
     * 执行知识构建任务的核心流程。
     *
     * @param taskId 任务 ID
     * @param forceBuild 是否绕过知识准入
     * @return 是否处理成功
     */
    private boolean process(Long taskId, boolean forceBuild) {
        TicketKnowledgeBuildTask task = taskMapper.findById(taskId);
        if (task == null) {
            log.warn("未找到知识构建任务：taskId={}", taskId);
            return false;
        }
        String lockedBy = "knowledge-worker-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.claim(taskId, lockedBy, now) <= 0) {
            // claim 失败说明任务已被其他 worker 抢占，或者当前状态不允许执行。
            log.info("知识构建任务被跳过：任务当前不可抢占，taskId={}, status={}", taskId, task.getStatus());
            return false;
        }

        try {
            Long ticketId = task.getTicketId();
            log.info("工单知识任务开始：taskId={}, ticketId={}", taskId, ticketId);
            KnowledgeAdmissionResult admission = forceBuild ? null : knowledgeAdmissionService.evaluate(ticketId);
            if (admission != null && !admission.autoApproved()) {
                // 准入未通过时，候选知识已由准入服务记录，当前 task 视为完成，避免反复重试。
                log.info("工单知识任务被准入规则拦截：taskId={}, ticketId={}, decision={}, score={}, reason={}",
                        taskId, ticketId, admission.getDecision(), admission.getQualityScore(), admission.getReason());
                taskMapper.markSuccess(taskId);
                return true;
            }
            Optional<TicketKnowledge> knowledge = ticketKnowledgeService.buildKnowledge(ticketId);
            if (knowledge.isEmpty()) {
                log.info("工单知识任务跳过：taskId={}, ticketId={} 不满足构建条件", taskId, ticketId);
                taskMapper.markSuccess(taskId);
                return true;
            }
            int chunks = embeddingService.embedKnowledge(knowledge.get()).size();
            taskMapper.markSuccess(taskId);
            log.info("工单知识任务完成：taskId={}, ticketId={}, knowledgeId={}, chunks={}",
                    taskId, ticketId, knowledge.get().getId(), chunks);
            return true;
        } catch (RuntimeException ex) {
            TicketKnowledgeBuildTask latest = taskMapper.findById(taskId);
            int retryCount = latest == null || latest.getRetryCount() == null ? 0 : latest.getRetryCount();
            boolean dead = retryCount + 1 >= maxRetries;
            // 使用指数退避控制失败任务重试频率，最多延迟到 60 分钟。
            LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(Math.min(60, (long) Math.pow(2, retryCount)));
            taskMapper.markFailed(taskId, limit(ex.getMessage(), 1000), nextRetryAt, dead);
            log.warn("工单知识任务失败：taskId={}, dead={}, reason={}", taskId, dead, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 截断错误信息，避免数据库错误字段过长。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
