package com.smartticket.agent.resilience;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 单轮调用预算。
 *
 * <p>该对象位于执行策略和具体执行器之间，用于记录本轮允许的 LLM、Tool、RAG 调用次数
 * 和总耗时边界。它只维护内存计数，不执行工具，不允许写库，也不会修改
 * session、memory、pendingAction 或 trace。</p>
 */
public class AgentTurnBudget {
    private final int maxLlmCalls;
    private final int maxToolCalls;
    private final int maxRagCalls;
    private final int maxInputTokens;
    private final long startedAtMillis;
    private final Duration timeout;
    private final AtomicInteger llmCalls = new AtomicInteger();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final AtomicInteger ragCalls = new AtomicInteger();

    public AgentTurnBudget(int maxLlmCalls, int maxToolCalls, int maxRagCalls, Duration timeout) {
        this(maxLlmCalls, maxToolCalls, maxRagCalls, 6000, timeout);
    }

    public AgentTurnBudget(int maxLlmCalls, int maxToolCalls, int maxRagCalls, int maxInputTokens, Duration timeout) {
        this.maxLlmCalls = Math.max(maxLlmCalls, 0);
        this.maxToolCalls = Math.max(maxToolCalls, 0);
        this.maxRagCalls = Math.max(maxRagCalls, 0);
        this.maxInputTokens = Math.max(maxInputTokens, 500);
        this.timeout = timeout;
        this.startedAtMillis = System.currentTimeMillis();
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    public int getMaxLlmCalls() {
        return maxLlmCalls;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public int getMaxRagCalls() {
        return maxRagCalls;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int nextLlmCall() {
        return llmCalls.incrementAndGet();
    }

    public int nextToolCall() {
        return toolCalls.incrementAndGet();
    }

    public int nextRagCall() {
        return ragCalls.incrementAndGet();
    }

    public long elapsedMillis() {
        return System.currentTimeMillis() - startedAtMillis;
    }
}
