package com.smartticket.agent.tool.core;

import com.smartticket.agent.model.AgentIntent;

/**
 * Agent Tool 统一接口。
 */
public interface AgentTool {
    String name();

    boolean support(AgentIntent intent);

    AgentToolMetadata metadata();

    AgentToolResult execute(AgentToolRequest request);
}
