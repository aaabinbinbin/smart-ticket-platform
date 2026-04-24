package com.smartticket.agent.model;

import com.smartticket.agent.planner.AgentPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 对话应用层结果，不绑定 HTTP 协议。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResult {
    /**
     * 当前会话 ID，用于多轮对话中读取和更新短期上下文。
     */
    private String sessionId;

    /**
     * 本轮识别出的业务意图编码。
     */
    private String intent;

    /**
     * 面向用户的回复文本。
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
     * 本轮执行计划，便于接口层透出当前执行阶段和下一步动作。
     */
    private AgentPlan plan;

    /**
     * 本轮 Agent trace ID，用于查询完整执行轨迹。
     */
    private String traceId;
}
