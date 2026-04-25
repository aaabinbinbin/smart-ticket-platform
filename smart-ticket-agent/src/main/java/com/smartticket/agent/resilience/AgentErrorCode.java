package com.smartticket.agent.resilience;

/**
 * Agent 高压治理错误码。
 *
 * <p>该枚举位于 Agent 主链的工程保护层，用于把限流、session lock、预算超限和超时等
 * 非业务失败统一表达为可审计事实。它不执行写操作，也不会修改 session、memory、
 * pendingAction 或 trace。</p>
 */
public enum AgentErrorCode {
    /**
     * 当前用户或全局请求量超过限流阈值。
     */
    AGENT_RATE_LIMITED,

    /**
     * 同一 sessionId 已有请求正在处理，当前请求被拒绝进入主链。
     */
    AGENT_SESSION_BUSY,

    /**
     * 本轮 LLM、Tool、RAG 或总耗时预算已耗尽。
     */
    AGENT_BUDGET_EXCEEDED,

    /**
     * 本轮执行超过策略允许的总耗时。
     */
    AGENT_TIMEOUT,

    /**
     * LLM 或 RAG 等增强能力不可用，本轮已尝试走确定性降级路径。
     */
    AGENT_DEGRADED
}
