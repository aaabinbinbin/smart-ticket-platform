package com.smartticket.api.controller.rag;

import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.rag.model.RetrievalRequest;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.service.RetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 检索 HTTP 入口，提供直接检索接口用于验证 PGVECTOR 主路径。
 *
 * <p>该控制器绕开 Agent 路由，直接调用 RetrievalService 执行向量检索，
 * 方便验收 PGVECTOR 路径是否正常工作。</p>
 */
@RestController
@RequestMapping("/api/rag")
@Tag(name = "RAG 检索", description = "直接 RAG 检索接口，用于验证 PGVECTOR 主路径")
public class RagController {
    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    /** RAG 历史知识检索服务。 */
    private final RetrievalService retrievalService;

    /** 当前登录用户解析器。 */
    private final CurrentUserResolver currentUserResolver;

    /**
     * 创建 RAG 检索控制器。
     *
     * @param retrievalService RAG 历史知识检索服务
     * @param currentUserResolver 当前登录用户解析器
     */
    public RagController(RetrievalService retrievalService, CurrentUserResolver currentUserResolver) {
        this.retrievalService = retrievalService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * 执行 RAG 知识检索。
     *
     * @param authentication 当前认证信息
     * @param query 检索查询文本
     * @param topK 返回的命中条数上限（默认 5，最大 10）
     * @return RAG 检索结果，包含 retrievalPath、fallbackUsed 和 hits
     */
    @GetMapping("/search")
    @Operation(summary = "RAG 知识检索", description = "直接调用 RAG 检索，不依赖 Agent 路由，方便验证 PGVECTOR 主路径")
    public ApiResponse<RetrievalResult> search(
            Authentication authentication,
            @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK
    ) {
        // 当前登录用户校验，确保已登录可访问
        CurrentUser currentUser = currentUserResolver.resolve(authentication);

        if (query == null || query.trim().isEmpty()) {
            return ApiResponse.failure("VALIDATION_FAILED", "检索查询文本不能为空");
        }
        log.info("RAG 检索请求：user={}, query='{}', topK={}", currentUser.getUsername(), query, topK);

        RetrievalResult result = retrievalService.retrieve(RetrievalRequest.builder()
                .queryText(query)
                .topK(topK)
                .rewrite(true)
                .build());

        log.info("RAG 检索路径={}，fallbackUsed={}，query='{}', hits={}",
                result.getRetrievalPath(), result.isFallbackUsed(), query, result.getHits().size());

        return ApiResponse.success(result);
    }
}
