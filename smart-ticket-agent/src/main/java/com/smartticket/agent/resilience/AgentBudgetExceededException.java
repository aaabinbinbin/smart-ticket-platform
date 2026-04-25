package com.smartticket.agent.resilience;

/**
 * Agent 单轮预算耗尽异常。
 *
 * <p>该异常由 {@link AgentTurnBudgetService} 抛出，用于在主链中及时中断超预算的
 * LLM/Tool/RAG 调用。异常本身不执行写操作，也不会修改 session、memory、pendingAction
 * 或 trace；上层 Facade 负责把它转换为兼容 `/api/agent/chat` 的失败回复。</p>
 */
public class AgentBudgetExceededException extends RuntimeException {
    private final AgentErrorCode errorCode;

    public AgentBudgetExceededException(AgentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentErrorCode getErrorCode() {
        return errorCode;
    }
}
