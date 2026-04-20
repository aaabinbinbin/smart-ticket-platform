package com.smartticket.api.dto.agent;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 对话响应。
 *
 * <p>该对象是 `/api/agent/chat` 的 HTTP 响应模型。业务结果保持结构化返回，
 * 便于前端展示和后续调试。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {
    /**
     * 当前会话 ID。
     */
    private String sessionId;

    /**
     * 本轮识别出的意图编码。
     */
    private String intent;

    /**
     * 面向用户展示的回复文本。
     */
    private String reply;

    /**
     * 意图路由明细。
     */
    private IntentRoute route;

    /**
     * 本轮执行后的会话上下文。
     */
    private AgentSessionContext context;

    /**
     * Tool 或编排器返回的结构化结果。
     */
    private Object result;

    /**
     * 当前请求处理时 Spring AI ChatClient 是否可用。
     */
    private boolean springAiChatReady;
}
