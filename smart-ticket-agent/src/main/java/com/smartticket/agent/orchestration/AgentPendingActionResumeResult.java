package com.smartticket.agent.orchestration;

import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * pending action 恢复执行后的结果。
 *
 * <p>编排器需要同时拿到恢复后的路由、合并后的计划和 Tool 观察结果，以便继续生成回复、
 * 更新上下文并决定是否继续保留 pending action。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPendingActionResumeResult {
    /** 本轮恢复动作对应的路由信息。 */
    private IntentRoute route;

    /** 合并补参后的 Tool 调用计划。 */
    private ToolCallPlan plan;

    /** Tool 执行或澄清后的观察结果。 */
    private AgentToolResult toolResult;
}
