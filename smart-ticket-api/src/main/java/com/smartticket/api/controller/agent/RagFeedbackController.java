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

/**
 * 检索增强反馈控制器。
 */
@RestController
@RequestMapping("/api/agent/rag-feedback")
@Tag(name = "RAG 反馈", description = "RAG 检索反馈")
public class RagFeedbackController {
    // 反馈服务
    private final RagFeedbackService feedbackService;
    // 当前用户解析器
    private final CurrentUserResolver currentUserResolver;

    /**
     * 构造检索增强反馈控制器。
     */
    public RagFeedbackController(RagFeedbackService feedbackService, CurrentUserResolver currentUserResolver) {
        this.feedbackService = feedbackService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 处理submit。
     */
    @PostMapping
    @Operation(summary = "提交 RAG 反馈")
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

    /**
     * 获取统计信息。
     */
    @GetMapping("/stats")
    @Operation(summary = "查询 RAG 反馈统计")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(feedbackService.stats());
    }
}
