package com.smartticket.agent.resilience;

import com.smartticket.agent.execution.AgentExecutionPolicy;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Agent 单轮预算控制服务。
 *
 * <p>该服务在 Agent 主链进入具体执行器前创建预算，并在 LLM、Tool、RAG 调用前进行扣减。
 * 它只负责工程保护，不决定业务 intent，不执行写操作，也不会修改 session、memory、
 * pendingAction 或 trace。</p>
 */
@Service
public class AgentTurnBudgetService {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 根据执行策略创建本轮预算。
     *
     * @param policy 当前轮执行策略，提供调用次数和超时上限
     * @return 可在线程内透传的预算对象
     */
    public AgentTurnBudget create(AgentExecutionPolicy policy) {
        if (policy == null) {
            return new AgentTurnBudget(0, 0, 0, DEFAULT_TIMEOUT);
        }
        return new AgentTurnBudget(
                policy.getMaxLlmCalls(),
                policy.getMaxToolCalls(),
                policy.getMaxRagCalls(),
                policy.getTimeout() == null ? DEFAULT_TIMEOUT : policy.getTimeout()
        );
    }

    /**
     * 消耗一次 LLM 调用预算。
     *
     * @param budget 当前轮预算
     */
    public void consumeLlmCall(AgentTurnBudget budget) {
        assertWithinTimeout(budget);
        if (budget == null || budget.nextLlmCall() <= budget.getMaxLlmCalls()) {
            return;
        }
        throw new AgentBudgetExceededException(AgentErrorCode.AGENT_BUDGET_EXCEEDED, "LLM 调用次数已超过本轮预算");
    }

    /**
     * 消耗一次 Tool 调用预算。
     *
     * @param budget 当前轮预算
     */
    public void consumeToolCall(AgentTurnBudget budget) {
        assertWithinTimeout(budget);
        if (budget == null || budget.nextToolCall() <= budget.getMaxToolCalls()) {
            return;
        }
        throw new AgentBudgetExceededException(AgentErrorCode.AGENT_BUDGET_EXCEEDED, "Tool 调用次数已超过本轮预算");
    }

    /**
     * 消耗一次 RAG 调用预算。
     *
     * @param budget 当前轮预算
     */
    public void consumeRagCall(AgentTurnBudget budget) {
        assertWithinTimeout(budget);
        if (budget == null || budget.nextRagCall() <= budget.getMaxRagCalls()) {
            return;
        }
        throw new AgentBudgetExceededException(AgentErrorCode.AGENT_BUDGET_EXCEEDED, "RAG 调用次数已超过本轮预算");
    }

    /**
     * 检查本轮总耗时是否仍在预算内。
     *
     * @param budget 当前轮预算
     */
    public void assertWithinTimeout(AgentTurnBudget budget) {
        if (budget == null || budget.getTimeout() == null) {
            return;
        }
        long timeoutMillis = budget.getTimeout().toMillis();
        if (timeoutMillis > 0 && budget.elapsedMillis() > timeoutMillis) {
            throw new AgentBudgetExceededException(AgentErrorCode.AGENT_TIMEOUT, "Agent 单轮处理已超过超时预算");
        }
    }
}
