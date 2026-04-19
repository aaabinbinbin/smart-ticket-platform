package com.smartticket.agent.llm.model;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提供给 LLM 的 fallback 工具调用计划视图。
 *
 * <p>该对象属于 LLM 层 DTO，用于避免 LLM 层直接依赖 orchestration 包中的 ToolCallPlan。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmFallbackToolCallPlan {
    /**
     * fallback 计划对应的意图。
     */
    private AgentIntent intent;

    /**
     * fallback 计划准备调用的 Tool 名称。
     */
    private String toolName;

    /**
     * fallback 计划携带的参数。
     */
    private AgentToolParameters parameters;

    /**
     * fallback 计划原因。
     */
    private String reason;
}
