package com.smartticket.rag.service;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.knowledge.TicketKnowledgeCandidateRepository;
import com.smartticket.biz.service.knowledge.TicketKnowledgeBuildTaskService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识候选人工审核服务，负责管理员准入、拒绝以及审核通过后的强制知识构建。
 */
@Service
public class KnowledgeCandidateReviewService {
    /**
     * 知识候选仓储。
     */
    private final TicketKnowledgeCandidateRepository candidateRepository;

    /**
     * 知识构建任务服务，用于审核通过后创建入库任务。
     */
    private final TicketKnowledgeBuildTaskService taskService;

    /**
     * 知识构建任务处理器，用于审核通过后立即触发构建。
     */
    private final TicketKnowledgeBuildTaskProcessor taskProcessor;

    /**
     * 创建知识候选审核服务。
     *
     * @param candidateRepository 知识候选仓储
     * @param taskService 知识构建任务服务
     * @param taskProcessor 知识构建任务处理器
     */
    public KnowledgeCandidateReviewService(
            TicketKnowledgeCandidateRepository candidateRepository,
            TicketKnowledgeBuildTaskService taskService,
            TicketKnowledgeBuildTaskProcessor taskProcessor
    ) {
        this.candidateRepository = candidateRepository;
        this.taskService = taskService;
        this.taskProcessor = taskProcessor;
    }

    /**
     * 查询指定状态的知识候选列表，默认查询待人工审核候选。
     *
     * @param status 候选状态
     * @param limit 返回数量上限
     * @return 知识候选列表
     */
    public List<TicketKnowledgeCandidate> list(String status, int limit) {
        return candidateRepository.findByStatus(status == null || status.isBlank() ? "MANUAL_REVIEW" : status, limit);
    }

    /**
     * 查询知识候选详情。
     *
     * @param candidateId 知识候选 ID
     * @return 知识候选详情
     */
    public TicketKnowledgeCandidate detail(Long candidateId) {
        return requireCandidate(candidateId);
    }

    /**
     * 审核通过知识候选，并触发正式知识构建。
     *
     * @param reviewer 审核人
     * @param candidateId 知识候选 ID
     * @param comment 审核备注
     * @return 审核结果
     */
    @Transactional
    public KnowledgeCandidateReviewResult approve(CurrentUser reviewer, Long candidateId, String comment) {
        requireAdmin(reviewer);
        TicketKnowledgeCandidate candidate = requireCandidate(candidateId);
        candidate.setStatus(KnowledgeCandidateReviewDecision.MANUAL_APPROVED.name());
        candidate.setDecision(KnowledgeCandidateReviewDecision.MANUAL_APPROVED.name());
        candidate.setReviewComment(comment);
        candidate.setReviewedBy(reviewer.getUserId());
        candidate.setReviewedAt(LocalDateTime.now());
        candidateRepository.save(candidate);

        TicketKnowledgeBuildTask task = taskService.createPending(candidate.getTicketId());
        // 人工审核通过已经等价于准入确认，因此这里使用 forceBuild 跳过自动准入。
        boolean buildSucceeded = task != null && taskProcessor.forceBuild(task.getId());
        return KnowledgeCandidateReviewResult.builder()
                .candidate(candidate)
                .task(task)
                .buildTriggered(task != null)
                .buildSucceeded(buildSucceeded)
                .build();
    }

    /**
     * 审核拒绝知识候选，不触发正式知识构建。
     *
     * @param reviewer 审核人
     * @param candidateId 知识候选 ID
     * @param comment 审核备注
     * @return 审核结果
     */
    @Transactional
    public KnowledgeCandidateReviewResult reject(CurrentUser reviewer, Long candidateId, String comment) {
        requireAdmin(reviewer);
        TicketKnowledgeCandidate candidate = requireCandidate(candidateId);
        candidate.setStatus(KnowledgeCandidateReviewDecision.MANUAL_REJECTED.name());
        candidate.setDecision(KnowledgeCandidateReviewDecision.MANUAL_REJECTED.name());
        candidate.setReviewComment(comment);
        candidate.setReviewedBy(reviewer.getUserId());
        candidate.setReviewedAt(LocalDateTime.now());
        candidateRepository.save(candidate);
        return KnowledgeCandidateReviewResult.builder()
                .candidate(candidate)
                .buildTriggered(false)
                .buildSucceeded(false)
                .build();
    }

    /**
     * 查询知识候选，不存在则抛出业务异常。
     *
     * @param candidateId 知识候选 ID
     * @return 知识候选
     */
    private TicketKnowledgeCandidate requireCandidate(Long candidateId) {
        TicketKnowledgeCandidate candidate = candidateRepository.findById(candidateId);
        if (candidate == null) {
            throw new BusinessException(BusinessErrorCode.TICKET_NOT_FOUND, "知识候选不存在");
        }
        return candidate;
    }

    /**
     * 校验审核人必须是管理员。
     *
     * @param reviewer 审核人
     */
    private void requireAdmin(CurrentUser reviewer) {
        if (reviewer == null || !reviewer.isAdmin()) {
            throw new BusinessException(BusinessErrorCode.ADMIN_REQUIRED);
        }
    }
}
