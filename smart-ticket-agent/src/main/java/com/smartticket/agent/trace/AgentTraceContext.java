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
    // 轨迹ID
    private final String traceId;
    // 会话ID
    private final String sessionId;
    // 用户ID
    private final Long userId;
    // rawInput
    private final String rawInput;
    private final long startedAtMillis = System.currentTimeMillis();
    private final List<AgentTraceStep> steps = new ArrayList<>();
    // 提示词Version
    @Setter
    private String promptVersion;

    /**
     * 构造智能体轨迹上下文。
     */
    public AgentTraceContext(String traceId, String sessionId, Long userId, String rawInput) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.rawInput = rawInput;
    }
}
