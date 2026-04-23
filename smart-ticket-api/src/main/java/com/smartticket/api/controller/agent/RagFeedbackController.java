package com.smartticket.api.controller.agent;

import com.smartticket.api.dto.agent.RagFeedbackRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.domain.entity.RagFeedback;
import com.smartticket.rag.service.RagFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/rag-feedback")
@Tag(name = "RAG Feedback", description = "RAG retrieval feedback")
public class RagFeedbackController {
    private final RagFeedbackService feedbackService;
    private final CurrentUserResolver currentUserResolver;

    public RagFeedbackController(RagFeedbackService feedbackService, CurrentUserResolver currentUserResolver) {
        this.feedbackService = feedbackService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    @Operation(summary = "Submit RAG feedback")
    public ApiResponse<RagFeedback> submit(Authentication authentication, @Valid @RequestBody RagFeedbackRequest request) {
        Long userId = currentUserResolver.resolve(authentication).getUserId();
        return ApiResponse.success(feedbackService.submit(
                request.getKnowledgeId(),
                request.getTicketId(),
                request.getSessionId(),
                request.getQueryText(),
                request.getFeedbackType(),
                request.getComment(),
                userId
        ));
    }

    @GetMapping("/stats")
    @Operation(summary = "Query RAG feedback stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(feedbackService.stats());
    }
}
