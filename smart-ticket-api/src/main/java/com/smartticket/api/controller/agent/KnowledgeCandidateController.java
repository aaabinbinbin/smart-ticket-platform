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

@Validated
@RestController
@RequestMapping("/api/agent/knowledge-candidates")
@Tag(name = "Knowledge Candidates", description = "Knowledge candidate manual review")
public class KnowledgeCandidateController {
    private final KnowledgeCandidateReviewService reviewService;
    private final CurrentUserResolver currentUserResolver;

    public KnowledgeCandidateController(
            KnowledgeCandidateReviewService reviewService,
            CurrentUserResolver currentUserResolver
    ) {
        this.reviewService = reviewService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @Operation(summary = "List knowledge candidates")
    public ApiResponse<List<TicketKnowledgeCandidate>> list(
            @RequestParam(value = "status", required = false) String status,
            @Min(1) @Max(100) @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return ApiResponse.success(reviewService.list(status, limit));
    }

    @GetMapping("/{candidateId}")
    @Operation(summary = "Get knowledge candidate detail")
    public ApiResponse<TicketKnowledgeCandidate> detail(@PathVariable("candidateId") Long candidateId) {
        return ApiResponse.success(reviewService.detail(candidateId));
    }

    @PostMapping("/{candidateId}/approve")
    @Operation(summary = "Approve candidate and build knowledge")
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

    @PostMapping("/{candidateId}/reject")
    @Operation(summary = "Reject knowledge candidate")
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
