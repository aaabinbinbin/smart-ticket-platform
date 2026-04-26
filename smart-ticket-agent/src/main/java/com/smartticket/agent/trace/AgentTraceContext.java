package com.smartticket.agent.trace;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 智能体轨迹上下文类。
 */
@Getter
public class AgentTraceContext {
    private final String traceId;
    private final String sessionId;
    private final Long userId;
    private final String rawInput;
    private final long startedAtMillis = System.currentTimeMillis();
    private final List<AgentTraceStep> steps = new ArrayList<>();
    /** LLM 在 ReAct 循环中的完整推理链，每个元素是一次工具调用前的思考过程。 */
    private final List<String> reasoningChain = new ArrayList<>();
    @Setter
    private String promptVersion;
    @Setter
    private Integer inputTokens;
    @Setter
    private Integer outputTokens;

    public AgentTraceContext(String traceId, String sessionId, Long userId, String rawInput) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.rawInput = rawInput;
    }
}
