package com.smartticket.agent.trace;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
public class AgentTraceContext {
    private final String traceId;
    private final String sessionId;
    private final Long userId;
    private final String rawInput;
    private final long startedAtMillis = System.currentTimeMillis();
    private final List<AgentTraceStep> steps = new ArrayList<>();
    @Setter
    private String promptVersion;

    public AgentTraceContext(String traceId, String sessionId, Long userId, String rawInput) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.rawInput = rawInput;
    }
}
