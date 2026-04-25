package com.smartticket.agent.stream;

import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.IntentRoute;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 基于 Spring MVC SseEmitter 的 Agent 事件输出器。
 *
 * <p>该实现位于 P7 SSE 输出层，只负责把 Agent 主链中的 accepted、route、status、delta、
 * final、error 事件写入 HTTP 流。final 事件会直接发送完整 AgentChatResult，确保同步接口和
 * 流式接口复用同一个业务结果。它不执行写操作，也不会修改 session、memory、pendingAction 或 trace。</p>
 */
public class SseAgentEventSink implements AgentEventSink {
    private static final Logger log = LoggerFactory.getLogger(SseAgentEventSink.class);

    private final SseEmitter emitter;
    private final String sessionId;
    private volatile boolean closed;

    public SseAgentEventSink(SseEmitter emitter, String sessionId) {
        this.emitter = emitter;
        this.sessionId = sessionId;
    }

    @Override
    public void accepted(String sessionId) {
        send(AgentStreamEventType.ACCEPTED, AgentStreamEvent.builder()
                .type(AgentStreamEventType.ACCEPTED)
                .sessionId(sessionId)
                .message("accepted")
                .build());
    }

    @Override
    public void status(String message) {
        send(AgentStreamEventType.STATUS, AgentStreamEvent.builder()
                .type(AgentStreamEventType.STATUS)
                .sessionId(sessionId)
                .message(message)
                .build());
    }

    @Override
    public void route(IntentRoute route) {
        send(AgentStreamEventType.ROUTE, AgentStreamEvent.builder()
                .type(AgentStreamEventType.ROUTE)
                .sessionId(sessionId)
                .route(route)
                .message(route == null || route.getIntent() == null ? null : route.getIntent().name())
                .build());
    }

    @Override
    public void delta(String text) {
        send(AgentStreamEventType.DELTA, AgentStreamEvent.builder()
                .type(AgentStreamEventType.DELTA)
                .sessionId(sessionId)
                .message(text)
                .build());
    }

    @Override
    public void finalResult(AgentChatResult result) {
        // final 事件直接发送完整 AgentChatResult，避免前端需要再从包装事件里拆业务结果。
        send(AgentStreamEventType.FINAL, result);
    }

    @Override
    public void error(String errorCode, String message, String traceId) {
        send(AgentStreamEventType.ERROR, AgentStreamEvent.builder()
                .type(AgentStreamEventType.ERROR)
                .sessionId(sessionId)
                .errorCode(errorCode)
                .message(message)
                .traceId(traceId)
                .build());
    }

    @Override
    public void closeQuietly() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            emitter.send(SseEmitter.event().name(AgentStreamEventType.DONE.eventName()).data("{\"done\":true}"));
        } catch (IOException | IllegalStateException ex) {
            log.debug("发送 SSE done 事件失败: sessionId={}, reason={}", sessionId, ex.getMessage());
        }
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            log.debug("关闭 Agent SSE 输出时客户端可能已断开: sessionId={}, reason={}", sessionId, ex.getMessage());
        }
    }

    private void send(AgentStreamEventType type, Object data) {
        if (closed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(type.eventName()).data(data));
        } catch (IOException | IllegalStateException ex) {
            // SSE 断开不能反向影响主业务执行；后续 finally 会释放 session lock。
            closed = true;
            log.warn("Agent SSE 事件输出失败: sessionId={}, event={}, reason={}", sessionId, type.eventName(), ex.getMessage());
        }
    }
}
