package com.smartticket.agent.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentTurnBudgetService} 单轮预算测试。
 *
 * <p>该测试验证 P6 对 LLM、Tool、RAG 和超时预算的硬边界。预算服务只负责阻断继续执行，
 * 不触碰 session、memory、pendingAction 或 trace。</p>
 */
class AgentTurnBudgetServiceTest {
    private final AgentTurnBudgetService service = new AgentTurnBudgetService();

    @Test
    void consumeToolCallShouldThrowWhenBudgetExceeded() {
        AgentTurnBudget budget = new AgentTurnBudget(0, 1, 0, Duration.ofSeconds(30));

        service.consumeToolCall(budget);
        AgentBudgetExceededException ex = assertThrows(AgentBudgetExceededException.class,
                () -> service.consumeToolCall(budget));

        assertEquals(AgentErrorCode.AGENT_BUDGET_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void consumeRagCallShouldThrowWhenBudgetExceeded() {
        AgentTurnBudget budget = new AgentTurnBudget(0, 0, 0, Duration.ofSeconds(30));

        AgentBudgetExceededException ex = assertThrows(AgentBudgetExceededException.class,
                () -> service.consumeRagCall(budget));

        assertEquals(AgentErrorCode.AGENT_BUDGET_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void assertWithinTimeoutShouldThrowWhenElapsedExceedsBudget() throws InterruptedException {
        AgentTurnBudget budget = new AgentTurnBudget(0, 0, 0, Duration.ofMillis(1));

        Thread.sleep(5L);
        AgentBudgetExceededException ex = assertThrows(AgentBudgetExceededException.class,
                () -> service.assertWithinTimeout(budget));

        assertEquals(AgentErrorCode.AGENT_TIMEOUT, ex.getErrorCode());
    }
}
