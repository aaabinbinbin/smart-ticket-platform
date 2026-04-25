package com.smartticket.agent.stream;

/**
 * Agent 流式事件类型。
 *
 * <p>该枚举位于 P7 SSE 输出层，用来约束前端可接收的事件名称。它只描述输出协议，
 * 不参与意图路由、不执行写操作，也不会修改 session、memory、pendingAction 或 trace。</p>
 */
public enum AgentStreamEventType {
    /**
     * 服务端已接受请求，后续会继续输出状态或最终结果。
     */
    ACCEPTED("accepted"),

    /**
     * 已完成意图路由。
     */
    ROUTE("route"),

    /**
     * 主链执行状态提示。
     */
    STATUS("status"),

    /**
     * 只读总结的增量文本事件，当前阶段预留给 ReAct 文本输出。
     */
    DELTA("delta"),

    /**
     * 本轮最终结果，data 必须是完整 AgentChatResult。
     */
    FINAL("final"),

    /**
     * 本轮发生错误或被工程保护拒绝。
     */
    ERROR("error"),

    /**
     * SSE 流式输出正常结束。
     */
    DONE("done");

    private final String eventName;

    AgentStreamEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
