package com.smartticket.api.controller.agent;

import com.smartticket.api.dto.agent.KnowledgeCandidateReviewRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.domain.entity.TicketKnowledgeCandidate;
import com.smartticket.rag.service.KnowledgeCandidateReviewResult;
import com.smartticket.rag.service.KnowledgeCandidateReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识候选控制器。
 */
@Validated
@RestController
@RequestMapping("/api/agent/knowledge-candidates")
@Tag(name = "知识候选", description = "知识候选人工审核")
public class KnowledgeCandidateController {
    // 审核服务
    private final KnowledgeCandidateReviewService reviewService;
    // 当前用户解析器
    private final CurrentUserResolver currentUserResolver;

    /**
     * 构造知识候选控制器。
     */
    public KnowledgeCandidateController(
            KnowledgeCandidateReviewService reviewService,
            CurrentUserResolver currentUserResolver
    ) {
        this.reviewService = reviewService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 处理列表。
     */
    @GetMapping
    @Operation(summary = "查询知识候选列表")
    public ApiResponse<List<TicketKnowledgeCandidate>> list(
            @RequestParam(value = "status", required = false) String status,
            @Min(1) @Max(100) @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return ApiResponse.success(reviewService.list(status, limit));
    }

    /**
     * 处理详情。
     */
    @GetMapping("/{candidateId}")
    @Operation(summary = "获取知识候选详情")
    public ApiResponse<TicketKnowledgeCandidate> detail(@PathVariable("candidateId") Long candidateId) {
        return ApiResponse.success(reviewService.detail(candidateId));
    }

    /**
     * 处理approve。
     */
    @PostMapping("/{candidateId}/approve")
    @Operation(summary = "审核通过候选并构建知识")
    public ApiResponse<KnowledgeCandidateReviewResult> approve(
            Authentication authentication,
            @PathVariable("candidateId") Long candidateId,
            @Valid @RequestBody(required = false) KnowledgeCandidateReviewRequest request
    ) {
        return ApiResponse.success(reviewService.approve(
                currentUserResolver.resolve(authentication),
                candidateId,
                request == null ? null : request.getComment()
        ));
    }

    /**
     * 处理reject。
     */
    @PostMapping("/{candidateId}/reject")
    @Operation(summary = "拒绝知识候选")
    public ApiResponse<KnowledgeCandidateReviewResult> reject(
            Authentication authentication,
            @PathVariable("candidateId") Long candidateId,
            @Valid @RequestBody(required = false) KnowledgeCandidateReviewRequest request
    ) {
        return ApiResponse.success(reviewService.reject(
                currentUserResolver.resolve(authentication),
                candidateId,
                request == null ? null : request.getComment()
        ));
    }
}
