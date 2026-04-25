package com.smartticket.agent.stream;

import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.IntentRoute;

/**
 * Agent 单轮事件输出接口。
 *
 * <p>该接口位于 Agent 主链与 HTTP 输出协议之间，同步接口使用 Noop 实现，SSE 接口使用
 * SseAgentEventSink。Sink 只单向输出 accepted、route、status、final、error 等事件，
 * 不反向影响业务执行，不执行写操作，也不会修改 session、memory、pendingAction 或 trace。</p>
 */
public interface AgentEventSink {

    /**
     * 通知请求已被接受。
     *
     * @param sessionId 会话 ID
     */
    void accepted(String sessionId);

    /**
     * 输出主链状态提示。
     *
     * @param message 状态文案
     */
    void status(String message);

    /**
     * 输出意图路由结果。
     *
     * @param route 当前轮路由结果
     */
    void route(IntentRoute route);

    /**
     * 输出只读总结增量文本。
     *
     * @param text 增量文本
     */
    void delta(String text);

    /**
     * 输出最终结果。
     *
     * @param result 完整 AgentChatResult
     */
    void finalResult(AgentChatResult result);

    /**
     * 输出错误事件。
     *
     * @param errorCode 错误码
     * @param message 错误原因
     * @param traceId traceId
     */
    void error(String errorCode, String message, String traceId);

    /**
     * 安静关闭输出资源。
     */
    void closeQuietly();
}
