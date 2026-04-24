package com.smartticket.agent.planner;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 智能体执行计划编排器，负责把路由结果转换为可推进、可追踪的计划状态。
 */
@Service
public class AgentPlanner {
    /**
     * 技能注册表，用于根据意图推导计划中下一步要调用的技能。
     */
    private final SkillRegistry skillRegistry;

    /**
     * 创建智能体计划编排器。
     *
     * @param skillRegistry 技能注册表
     */
    public AgentPlanner(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 根据会话中的旧计划和当前路由结果构建或恢复执行计划。
     *
     * @param context 当前会话上下文
     * @param route 当前意图路由结果
     * @return 本轮对话应使用的执行计划
     */
    public AgentPlan buildOrLoadPlan(AgentSessionContext context, IntentRoute route) {
        AgentPlan existing = context == null ? null : context.getPlanState();
        if (existing != null && existing.getIntent() == route.getIntent() && existing.isWaitingForUser()) {
            // 已经在等待用户补充信息的同意图计划，继续保持补槽推进，不重新生成计划。
            existing.setCurrentStage(AgentPlanStage.COLLECT_REQUIRED_SLOTS);
            existing.setNextAction(AgentPlanAction.COLLECT_SLOTS);
            existing.setUpdatedAt(LocalDateTime.now());
            return existing;
        }
        AgentSkill skill = skillRegistry.requireByIntent(route.getIntent());
        AgentPlan plan = AgentPlan.builder()
                .goal(goalFor(route.getIntent()))
                .intent(route.getIntent())
                .currentStage(AgentPlanStage.ROUTE_INTENT)
                .nextAction(AgentPlanAction.EXECUTE_TOOL)
                .nextSkillCode(skill.skillCode())
                .riskLevel(skill.riskLevel())
                .waitingForUser(false)
                .requiredSlots(new ArrayList<>(skill.inputSchema()))
                .plannedSteps(templateSteps(route.getIntent(), skill))
                .updatedAt(LocalDateTime.now())
                .build();
        if (route.getConfidence() < 0.50d) {
            // 低置信度请求先进入澄清阶段，避免误触发真实业务动作。
            plan.setCurrentStage(AgentPlanStage.WAIT_USER);
            plan.setNextAction(AgentPlanAction.CLARIFY_INTENT);
            plan.setWaitingForUser(true);
        } else if (skill.riskLevel() == ToolRiskLevel.HIGH_RISK_WRITE && !skill.canAutoExecute()) {
            // 高风险写操作必须等待用户二次确认，不能直接自动执行。
            plan.setCurrentStage(AgentPlanStage.WAIT_USER);
            plan.setNextAction(AgentPlanAction.CONFIRM_HIGH_RISK);
            plan.setWaitingForUser(true);
        }
        return plan;
    }

    /**
     * 将计划推进到意图澄清状态。
     *
     * @param plan 当前执行计划
     * @param summary 澄清原因摘要
     */
    public void markClarify(AgentPlan plan, String summary) {
        complete(plan, AgentPlanStage.ROUTE_INTENT, AgentPlanAction.CLARIFY_INTENT, null, summary);
        plan.setCurrentStage(AgentPlanStage.WAIT_USER);
        plan.setNextAction(AgentPlanAction.CLARIFY_INTENT);
        plan.setWaitingForUser(true);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 在执行 Tool 或 Skill 前，把计划推进到执行阶段。
     *
     * @param plan 当前执行计划
     */
    public void beforeExecute(AgentPlan plan) {
        if (plan == null) {
            return;
        }
        plan.setCurrentStage(AgentPlanStage.EXECUTE_SKILL);
        plan.setNextAction(AgentPlanAction.EXECUTE_TOOL);
        plan.setWaitingForUser(false);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 将计划推进到高风险操作确认状态。
     *
     * @param plan 当前执行计划
     * @param summary 需要确认的原因摘要
     */
    public void markNeedConfirmation(AgentPlan plan, String summary) {
        if (plan == null) {
            return;
        }
        complete(plan, AgentPlanStage.WAIT_USER, AgentPlanAction.CONFIRM_HIGH_RISK, plan.getNextSkillCode(), summary);
        plan.setCurrentStage(AgentPlanStage.WAIT_USER);
        plan.setNextAction(AgentPlanAction.CONFIRM_HIGH_RISK);
        plan.setWaitingForUser(true);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 根据 Tool 执行结果更新计划状态。
     *
     * @param plan 当前执行计划
     * @param result Tool 执行结果
     */
    public void afterTool(AgentPlan plan, AgentToolResult result) {
        if (plan == null || result == null) {
            return;
        }
        complete(plan, AgentPlanStage.EXECUTE_SKILL, AgentPlanAction.EXECUTE_TOOL, result.getToolName(), result.getStatus().name());
        if (result.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            // Tool 明确要求补充信息时，把缺失字段回写到计划，供前端或调试端展示。
            plan.setCurrentStage(AgentPlanStage.COLLECT_REQUIRED_SLOTS);
            plan.setNextAction(AgentPlanAction.COLLECT_SLOTS);
            plan.setWaitingForUser(true);
            plan.setRequiredSlots(extractMissingFields(result.getData()));
        } else {
            plan.setCurrentStage(AgentPlanStage.SUMMARIZE_RESULT);
            plan.setNextAction(AgentPlanAction.RETURN_RESULT);
            plan.setWaitingForUser(false);
        }
        plan.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 记录一个已经完成的计划步骤。
     *
     * @param plan 当前执行计划
     * @param stage 完成时所在阶段
     * @param action 完成的动作
     * @param skillCode 关联的技能编码
     * @param summary 执行摘要
     */
    private void complete(AgentPlan plan, AgentPlanStage stage, AgentPlanAction action, String skillCode, String summary) {
        if (plan == null) {
            return;
        }
        plan.getCompletedSteps().add(AgentPlanStep.builder()
                .stage(stage)
                .action(action)
                .skillCode(skillCode)
                .summary(summary)
                .completed(true)
                .completedAt(LocalDateTime.now())
                .build());
    }

    /**
     * 从 Tool 返回数据中提取缺失字段列表。
     *
     * @param data Tool 结果中的结构化数据
     * @return 缺失字段列表
     */
    private List<AgentToolParameterField> extractMissingFields(Object data) {
        if (!(data instanceof List<?> rawList)) {
            return List.of();
        }
        List<AgentToolParameterField> fields = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof AgentToolParameterField field) {
                fields.add(field);
            }
        }
        return fields;
    }

    /**
     * 把业务意图转换为计划目标编码。
     *
     * @param intent 业务意图
     * @return 计划目标编码
     */
    private String goalFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> "create_ticket";
            case QUERY_TICKET -> "query_ticket";
            case TRANSFER_TICKET -> "transfer_ticket";
            case SEARCH_HISTORY -> "search_history";
        };
    }

    /**
     * 生成计划的标准步骤模板。
     *
     * @param intent 业务意图
     * @param skill 当前意图对应的技能
     * @return 计划步骤模板
     */
    private List<AgentPlanStep> templateSteps(AgentIntent intent, AgentSkill skill) {
        List<AgentPlanStep> steps = new ArrayList<>();
        steps.add(step(AgentPlanStage.ROUTE_INTENT, AgentPlanAction.CLARIFY_INTENT, null, "identify goal and ambiguity"));
        if (intent == AgentIntent.CREATE_TICKET) {
            steps.add(step(AgentPlanStage.COLLECT_REQUIRED_SLOTS, AgentPlanAction.COLLECT_SLOTS, skill.skillCode(), "collect title and description"));
            steps.add(step(AgentPlanStage.CHECK_CONTEXT, AgentPlanAction.EXECUTE_TOOL, skill.skillCode(), "check similar history before create"));
        } else {
            steps.add(step(AgentPlanStage.COLLECT_REQUIRED_SLOTS, AgentPlanAction.COLLECT_SLOTS, skill.skillCode(), "collect required tool parameters"));
        }
        if (skill.riskLevel() == ToolRiskLevel.HIGH_RISK_WRITE) {
            steps.add(step(AgentPlanStage.WAIT_USER, AgentPlanAction.CONFIRM_HIGH_RISK, skill.skillCode(), "confirm high risk operation"));
        }
        steps.add(step(AgentPlanStage.EXECUTE_SKILL, AgentPlanAction.EXECUTE_TOOL, skill.skillCode(), "execute selected skill"));
        steps.add(step(AgentPlanStage.SUMMARIZE_RESULT, AgentPlanAction.RETURN_RESULT, skill.skillCode(), "return result and next step"));
        return steps;
    }

    /**
     * 创建一个未完成的计划步骤。
     *
     * @param stage 计划阶段
     * @param action 下一步动作
     * @param skillCode 关联技能编码
     * @param summary 步骤摘要
     * @return 计划步骤
     */
    private AgentPlanStep step(AgentPlanStage stage, AgentPlanAction action, String skillCode, String summary) {
        return AgentPlanStep.builder()
                .stage(stage)
                .action(action)
                .skillCode(skillCode)
                .summary(summary)
                .completed(false)
                .build();
    }
}
