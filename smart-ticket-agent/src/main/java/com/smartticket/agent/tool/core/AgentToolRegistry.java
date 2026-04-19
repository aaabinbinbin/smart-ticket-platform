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
    private final List<AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> tools) {
        this.tools = List.copyOf(tools);
    }

    public Optional<AgentTool> findByIntent(AgentIntent intent) {
        return tools.stream()
                .filter(tool -> tool.support(intent))
                .findFirst();
    }

    public Optional<AgentTool> findByName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        String normalized = toolName.trim().toLowerCase(Locale.ROOT);
        return tools.stream()
                .filter(tool -> tool.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public AgentTool requireByIntent(AgentIntent intent) {
        return findByIntent(intent)
                .orElseThrow(() -> new IllegalStateException("No agent tool found for intent: " + intent));
    }

    public List<AgentTool> allTools() {
        return tools;
    }
}
