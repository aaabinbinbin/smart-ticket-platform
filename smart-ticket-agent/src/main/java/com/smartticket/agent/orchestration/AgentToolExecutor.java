package com.smartticket.agent.orchestration;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.biz.model.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * Agent Tool 执行器。
 *
 * <p>该类只负责把已被 {@link com.smartticket.agent.execution.AgentExecutionGuard} 放行的计划转换成
 * AgentToolRequest 并调用 Tool。执行前安全决策统一收敛在 Guard 中。</p>
 */
@Component
public class AgentToolExecutor {
    /**
     * 执行已通过 Guard 校验的 Tool。
     */
    public AgentToolResult execute(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            ToolCallPlan plan,
            AgentExecutionDecision decision
    ) {
        if (!decision.isAllowed()) {
            return decision.toToolResult(plan.getToolName());
        }
        AgentToolRequest toolRequest = AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(plan.getParameters())
                .build();
        return decision.getTool().execute(toolRequest);
    }
}
