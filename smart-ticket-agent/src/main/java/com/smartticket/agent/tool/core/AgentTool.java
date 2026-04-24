package com.smartticket.agent.tool.core;

import com.smartticket.agent.model.AgentIntent;

/**
 * Agent Tool 统一接口。
 */
public interface AgentTool {
    /**
     * 处理名称。
     */
    String name();

    /**
     * 处理支撑。
     */
    boolean support(AgentIntent intent);

    /**
     * 处理元数据。
     */
    AgentToolMetadata metadata();

    /**
     * 执行操作。
     */
    AgentToolResult execute(AgentToolRequest request);
}
