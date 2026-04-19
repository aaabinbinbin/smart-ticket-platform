package com.smartticket.agent.orchestration;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolRequestValidator;
import com.smartticket.agent.tool.parameter.AgentToolValidationResult;
import com.smartticket.biz.model.CurrentUser;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Agent Tool 执行器。
 *
 * <p>该类负责把已校验的计划转换成 AgentToolRequest，并在执行前做确认和必填参数校验。</p>
 */
@Component
public class AgentToolExecutor {
    /**
     * 通用 Tool 参数校验器。
     */
    private final AgentToolRequestValidator toolRequestValidator;

    public AgentToolExecutor(AgentToolRequestValidator toolRequestValidator) {
        this.toolRequestValidator = toolRequestValidator;
    }

    /**
     * 执行 Tool，或在缺少确认、缺少参数时返回 NEED_MORE_INFO。
     */
    public AgentToolResult executeOrClarify(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            ToolCallPlan plan,
            ToolCallPlanValidationResult validation
    ) {
        if (validation.isNeedConfirmation()) {
            return AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.NEED_MORE_INFO)
                    .toolName(validation.getTool().name())
                    .reply(validation.getReason())
                    .data(List.of())
                    .build();
        }

        AgentToolRequest toolRequest = AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(plan.getParameters())
                .build();

        AgentToolValidationResult parameterValidation = toolRequestValidator.validate(validation.getTool(), toolRequest);
        if (!parameterValidation.isValid()) {
            return AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.NEED_MORE_INFO)
                    .toolName(validation.getTool().name())
                    .reply("请补充必要信息。")
                    .data(parameterValidation.getMissingFields())
                    .build();
        }
        return validation.getTool().execute(toolRequest);
    }
}
