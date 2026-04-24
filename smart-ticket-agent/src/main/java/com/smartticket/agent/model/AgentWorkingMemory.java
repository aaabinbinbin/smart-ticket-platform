package com.smartticket.agent.model;

import com.smartticket.agent.tool.parameter.AgentToolParameters;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体Working记忆类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkingMemory {
    // 当前任务阶段
    private String currentTaskStage;
    // last工具Name
    private String lastToolName;
    // last工具状态
    private String lastToolStatus;
    // last工具摘要
    private String lastToolSummary;
    // 更新时间
    private LocalDateTime updatedAt;

    @Builder.Default
    private Map<String, Object> collectedSlots = new LinkedHashMap<>();

    /**
     * 处理from。
     */
    public static AgentWorkingMemory from(
            AgentWorkingMemory existing,
            IntentRoute route,
            AgentToolParameters parameters,
            String toolName,
            String toolStatus,
            String summary
    ) {
        AgentWorkingMemory memory = existing == null ? AgentWorkingMemory.builder().build() : existing;
        memory.setCurrentTaskStage(route == null ? null : route.getIntent().name());
        memory.setLastToolName(toolName);
        memory.setLastToolStatus(toolStatus);
        memory.setLastToolSummary(limit(summary, 500));
        memory.setUpdatedAt(LocalDateTime.now());
        memory.setCollectedSlots(mergeSlots(memory.getCollectedSlots(), parameters));
        return memory;
    }

    /**
     * 处理槽位信息。
     */
    private static Map<String, Object> mergeSlots(Map<String, Object> existing, AgentToolParameters parameters) {
        Map<String, Object> slots = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        if (parameters == null) {
            return slots;
        }
        putIfPresent(slots, "ticketId", parameters.getTicketId());
        putIfPresent(slots, "assigneeId", parameters.getAssigneeId());
        putIfPresent(slots, "title", parameters.getTitle());
        putIfPresent(slots, "type", parameters.getType());
        putIfPresent(slots, "category", parameters.getCategory());
        putIfPresent(slots, "priority", parameters.getPriority());
        return slots;
    }

    /**
     * 按存在时处理。
     */
    private static void putIfPresent(Map<String, Object> slots, String key, Object value) {
        if (value != null) {
            slots.put(key, value);
        }
    }

    /**
     * 处理limit。
     */
    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
