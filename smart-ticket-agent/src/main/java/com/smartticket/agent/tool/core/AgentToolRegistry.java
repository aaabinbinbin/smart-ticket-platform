package com.smartticket.agent.tool.core;

import com.smartticket.agent.model.AgentIntent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Agent Tool 注册表。
 */
@Component
public class AgentToolRegistry {
    // tools
    private final List<AgentTool> tools;

    /**
     * 构造智能体工具注册表。
     */
    public AgentToolRegistry(List<AgentTool> tools) {
        this.tools = List.copyOf(tools);
    }

    /**
     * 查询按意图。
     */
    public Optional<AgentTool> findByIntent(AgentIntent intent) {
        return tools.stream()
                .filter(tool -> tool.support(intent))
                .findFirst();
    }

    /**
     * 按名称查询。
     */
    public Optional<AgentTool> findByName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        String normalized = toolName.trim().toLowerCase(Locale.ROOT);
        return tools.stream()
                .filter(tool -> tool.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    /**
     * 校验按意图。
     */
    public AgentTool requireByIntent(AgentIntent intent) {
        return findByIntent(intent)
                .orElseThrow(() -> new IllegalStateException("No agent tool found for intent: " + intent));
    }

    /**
     * 处理Tools。
     */
    public List<AgentTool> allTools() {
        return tools;
    }
}
