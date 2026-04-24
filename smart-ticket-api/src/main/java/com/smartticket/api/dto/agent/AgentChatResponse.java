package com.smartticket.api.dto.agent;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.planner.AgentPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 对话 HTTP 响应模型。
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
     * 本轮识别出的业务意图编码。
     */
    private String intent;

    /**
     * 面向用户展示的回复文本。
     */
    private String reply;

    /**
     * 意图路由明细，包含意图、置信度和路由原因。
     */
    private IntentRoute route;

    /**
     * 本轮执行后的 智能体会话上下文。
     */
    private AgentSessionContext context;

    /**
     * Tool 或编排器返回的结构化业务结果。
     */
    private Object result;

    /**
     * 当前请求处理时 Spring AI ChatClient 是否可用。
     */
    private boolean springAiChatReady;

    /**
     * 本轮执行计划，用于前端调试和执行链路复盘。
     */
    private AgentPlan plan;

    /**
     * 本轮 Agent trace ID，用于查询完整执行轨迹。
     */
    private String traceId;
}
