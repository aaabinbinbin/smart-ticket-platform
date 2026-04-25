package com.smartticket.agent.stream;

import com.smartticket.agent.model.IntentRoute;
import lombok.Builder;
import lombok.Data;

/**
 * Agent SSE 中间事件载荷。
 *
 * <p>该模型用于 accepted、route、status、delta、error 等非 final 事件，帮助前端展示执行进度。
 * final 事件直接发送完整 AgentChatResult，因此本类不会承载最终业务结果，也不会修改
 * session、memory、pendingAction 或 trace。</p>
 */
@Data
@Builder
public class AgentStreamEvent {
    /**
     * 当前事件类型。
     */
    private AgentStreamEventType type;

    /**
     * 当前会话 ID。
     */
    private String sessionId;

    /**
     * 状态或错误提示。
     */
    private String message;

    /**
     * 路由事件中的意图识别结果。
     */
    private IntentRoute route;

    /**
     * 错误事件中的结构化错误码。
     */
    private String errorCode;

    /**
     * 错误或最终状态关联的 traceId。
     */
    private String traceId;
}
