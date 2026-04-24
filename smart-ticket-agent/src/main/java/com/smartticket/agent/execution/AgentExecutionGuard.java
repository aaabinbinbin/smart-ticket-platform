package com.smartticket.agent.execution;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.orchestration.ToolCallPlanValidationResult;
import com.smartticket.agent.orchestration.ToolCallPlanValidator;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.parameter.AgentToolRequestValidator;
import com.smartticket.agent.tool.parameter.AgentToolValidationResult;
import com.smartticket.biz.model.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * 智能体执行边界守卫。
 *
 * <p>该组件把执行前的安全边界集中为平台能力：计划合法性、风险确认、必填参数都在这里统一判断。
 * 编排器只消费决策结果，Tool 执行器只负责执行已放行的 Tool。</p>
 */
@Component
public class AgentExecutionGuard {
    /** Tool 调用计划校验器，负责 toolName、intent、风险等级和确认要求。 */
    private final ToolCallPlanValidator planValidator;

    /** Tool 参数校验器，负责根据 Tool 元数据检查 requiredFields。 */
    private final AgentToolRequestValidator requestValidator;

    /**
     * 构造智能体Execution守卫。
     */
    public AgentExecutionGuard(
            ToolCallPlanValidator planValidator,
            AgentToolRequestValidator requestValidator
    ) {
        this.planValidator = planValidator;
        this.requestValidator = requestValidator;
    }

    /**
     * 对一次 Tool 调用计划做执行前决策。
     *
     * @param currentUser 当前登录用户
     * @param message 用户原始消息
     * @param context 当前会话上下文
     * @param route 当前路由结果
     * @param plan 待执行 Tool 调用计划
     * @return 执行前置决策
     */
    public AgentExecutionDecision check(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            ToolCallPlan plan
    ) {
        ToolCallPlanValidationResult planValidation = planValidator.validate(plan, message);
        if (!planValidation.isValid()) {
            return AgentExecutionDecision.rejected(planValidation.getReason());
        }
        if (planValidation.isNeedConfirmation()) {
            return AgentExecutionDecision.needConfirmation(planValidation.getTool(), planValidation.getReason());
        }

        AgentToolRequest toolRequest = AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(plan.getParameters())
                .build();
        AgentToolValidationResult parameterValidation = requestValidator.validate(planValidation.getTool(), toolRequest);
        if (!parameterValidation.isValid()) {
            return AgentExecutionDecision.needMoreInfo(
                    planValidation.getTool(),
                    parameterValidation.getMissingFields()
            );
        }
        return AgentExecutionDecision.allow(planValidation.getTool());
    }
}
