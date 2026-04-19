package com.smartticket.agent.orchestration;

import com.smartticket.agent.model.IntentRoute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 阶段八工作流 fallback 结果。
 *
 * <p>阶段九会优先尝试 LLM 工具调用计划，但任何计划生成或校验失败时，
 * 都会回退到这里保存的阶段八 route 和 ToolCallPlan。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkflowFallback {
    /**
     * 阶段八生成的最终路由结果。
     */
    private IntentRoute route;

    /**
     * 基于阶段八工作流构造出的工具调用计划。
     */
    private ToolCallPlan plan;
}
