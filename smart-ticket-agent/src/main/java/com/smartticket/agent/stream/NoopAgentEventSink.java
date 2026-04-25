package com.smartticket.agent.stream;

import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.IntentRoute;

/**
 * 同步接口使用的空事件输出器。
 *
 * <p>该实现用于保持 `/api/agent/chat` 的既有一次性响应行为：主链仍然会调用事件接口，
 * 但这里不会输出任何内容。它不执行写操作，也不会修改 session、memory、pendingAction 或 trace。</p>
 */
public enum NoopAgentEventSink implements AgentEventSink {
    /**
     * 单例空输出器。
     */
    INSTANCE;

    @Override
    public void accepted(String sessionId) {
    }

    @Override
    public void status(String message) {
    }

    @Override
    public void route(IntentRoute route) {
    }

    @Override
    public void delta(String text) {
    }

    @Override
    public void finalResult(AgentChatResult result) {
    }

    @Override
    public void error(String errorCode, String message, String traceId) {
    }

    @Override
    public void closeQuietly() {
    }
}
