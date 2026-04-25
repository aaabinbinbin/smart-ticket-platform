package com.smartticket.api.controller.agent;

import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.service.AgentFacade;
import com.smartticket.agent.stream.SseAgentEventSink;
import com.smartticket.api.dto.agent.AgentChatRequest;
import com.smartticket.api.dto.agent.AgentChatResponse;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 对话 HTTP 入口，只负责接收请求、解析当前用户并组装响应。
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "智能体对话", description = "智能体对话入口")
public class AgentController {
    private static final long AGENT_STREAM_TIMEOUT_MILLIS = 120_000L;

    /**
     * 智能体主链应用服务。
     */
    private final AgentFacade agentFacade;

    /**
     * 当前登录用户解析器。
     */
    private final CurrentUserResolver currentUserResolver;

    /**
     * Agent 专用线程池，避免占用公共 ForkJoinPool。
     */
    private final Executor agentExecutor;

    /**
     * 创建智能体对话控制器。
     *
     * @param agentFacade Agent 主链应用服务
     * @param currentUserResolver 当前登录用户解析器
     * @param agentExecutor Agent 专用线程池
     */
    public AgentController(AgentFacade agentFacade, CurrentUserResolver currentUserResolver,
                           @Qualifier("agentExecutor") Executor agentExecutor) {
        this.agentFacade = agentFacade;
        this.currentUserResolver = currentUserResolver;
        this.agentExecutor = agentExecutor;
    }

    /**
     * 执行一次 Agent 对话。
     *
     * @param authentication 当前认证信息
     * @param request 对话请求
     * @return Agent 对话响应
     */
    @PostMapping("/chat")
    @Operation(summary = "智能体对话", description = "执行智能体对话与工具编排")
    public ApiResponse<AgentChatResponse> chat(
            Authentication authentication,
            @Valid @RequestBody AgentChatRequest request
    ) {
        AgentChatResult result = agentFacade.chat(currentUserResolver.resolve(authentication), request.getSessionId(), request.getMessage());
        return ApiResponse.success(toResponse(result));
    }

    /**
     * 执行一次 Agent 流式对话。
     *
     * <p>该接口只新增 SSE 输出体验，不改变同步 `/api/agent/chat` 的协议，也不在 Controller 中编排
     * Agent 策略。Controller 只解析当前用户、创建 SseEmitter，并把请求委派给 AgentFacade；
     * final 事件由 AgentFacade 发送完整 AgentChatResult。该方法不直接修改
     * session、memory、pendingAction 或 trace。</p>
     *
     * @param authentication 当前认证信息
     * @param request 对话请求
     * @return SSE emitter，事件包括 accepted、route、status、delta、final、error
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "智能体流式对话", description = "通过 SSE 输出智能体执行进度和最终结果")
    public SseEmitter chatStream(
            Authentication authentication,
            @Valid @RequestBody AgentChatRequest request
    ) {
        CurrentUser currentUser = currentUserResolver.resolve(authentication);
        SseEmitter emitter = new SseEmitter(AGENT_STREAM_TIMEOUT_MILLIS);
        SseAgentEventSink sink = new SseAgentEventSink(emitter, request.getSessionId());
        CompletableFuture.runAsync(() -> {
            try {
                agentFacade.chatStream(currentUser, request.getSessionId(), request.getMessage(), sink);
            } catch (RuntimeException ex) {
                // 异步线程中的异常不会再经过 MVC 异常处理器，必须转成 SSE error 事件后关闭连接。
                sink.error("AGENT_STREAM_FAILED", ex.getMessage(), null);
                sink.closeQuietly();
            }
        }, agentExecutor);
        return emitter;
    }

    /**
     * 将 agent 模块的应用层结果转换为 HTTP 响应 DTO。
     *
     * @param result Agent 应用层结果
     * @return HTTP 响应 DTO
     */
    private AgentChatResponse toResponse(AgentChatResult result) {
        return AgentChatResponse.builder()
                .sessionId(result.getSessionId())
                .intent(result.getIntent())
                .reply(result.getReply())
                .route(result.getRoute())
                .context(result.getContext())
                .result(result.getResult())
                .springAiChatReady(result.isSpringAiChatReady())
                .plan(result.getPlan())
                .traceId(result.getTraceId())
                .build();
    }
}
