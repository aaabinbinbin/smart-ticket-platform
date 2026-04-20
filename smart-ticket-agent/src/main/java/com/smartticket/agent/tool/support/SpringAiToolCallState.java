package com.smartticket.agent.tool.support;

import com.smartticket.agent.tool.core.AgentToolResult;
import lombok.Getter;
import lombok.Setter;

/**
 * Spring AI Tool Calling 单次调用状态。
 *
 * <p>Spring AI 原生 Tool 方法由模型触发，Facade 需要在调用结束后知道到底哪个 Tool
 * 被执行以及执行结果是什么。本对象作为 toolContext 中的可变状态传递，不保存到 Redis，
 * 只在一次请求内使用。</p>
 */
@Getter
@Setter
public class SpringAiToolCallState {
    /**
     * 本轮被 Spring AI 触发的 Tool 名称。
     */
    private String toolName;

    /**
     * 本轮 Tool 执行结果。
     */
    private AgentToolResult result;
}
