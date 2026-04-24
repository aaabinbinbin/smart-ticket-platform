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
import com.smartticket.agent.tool.support.SpringAiToolCallState.AgentToolCallRecord;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 智能体执行计划编排器。
 *
 * <p>不再预设执行步骤模板，而是记录 LLM 驱动的实际工具调用决策路径。
 * 计划状态仅维护当前阶段、等待状态和已完成步骤。</p>
 */
@Service
public class AgentPlanner {

    private final SkillRegistry skillRegistry;

    public AgentPlanner(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 构建或恢复执行计划。不再预设固定步骤模板，只做轻量初始化。
     */
    public AgentPlan buildOrLoadPlan(AgentSessionContext context, IntentRoute route) {
        AgentPlan existing = context == null ? null : context.getPlanState();
        if (existing != null && existing.getIntent() == route.getIntent() && existing.isWaitingForUser()) {
            // 已在等待用户补充信息的同意图计划，保持状态不重置
            existing.setCurrentStage(AgentPlanStage.WAIT_USER);
            existing.setNextAction(AgentPlanAction.CLARIFY_INTENT);
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
                .updatedAt(LocalDateTime.now())
                .build();

        if (route.getConfidence() < 0.50d) {
            plan.setCurrentStage(AgentPlanStage.WAIT_USER);
            plan.setNextAction(AgentPlanAction.CLARIFY_INTENT);
            plan.setWaitingForUser(true);
        }
        return plan;
    }

    /**
     * 将计划推进到意图澄清状态。
     */
    public void markClarify(AgentPlan plan, String summary) {
        if (plan == null) return;
        recordStep(plan, AgentPlanStage.ROUTE_INTENT, AgentPlanAction.CLARIFY_INTENT, null, summary);
        plan.setCurrentStage(AgentPlanStage.WAIT_USER);
        plan.setNextAction(AgentPlanAction.CLARIFY_INTENT);
        plan.setWaitingForUser(true);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 执行前的计划推进。
     */
    public void beforeExecute(AgentPlan plan) {
        if (plan == null) return;
        plan.setCurrentStage(AgentPlanStage.EXECUTE_SKILL);
        plan.setNextAction(AgentPlanAction.EXECUTE_TOOL);
        plan.setWaitingForUser(false);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 标记为高风险确认状态。
     */
    public void markNeedConfirmation(AgentPlan plan, String summary) {
        if (plan == null) return;
        recordStep(plan, AgentPlanStage.WAIT_USER, AgentPlanAction.CONFIRM_HIGH_RISK, plan.getNextSkillCode(), summary);
        plan.setCurrentStage(AgentPlanStage.WAIT_USER);
        plan.setNextAction(AgentPlanAction.CONFIRM_HIGH_RISK);
        plan.setWaitingForUser(true);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 工具执行后更新计划状态。
     */
    public void afterTool(AgentPlan plan, AgentToolResult result) {
        if (plan == null || result == null) return;
        recordStep(plan, AgentPlanStage.EXECUTE_SKILL, AgentPlanAction.EXECUTE_TOOL,
                result.getToolName(), result.getStatus().name());

        if (result.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
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
     * 记录 LLM 驱动的多步工具调用链（ReAct 循环记录）。
     * 每次在 Spring AI 的完整多步调用结束后调用此方法。
     */
    public void recordToolCalls(AgentPlan plan, List<AgentToolCallRecord> calls) {
        if (plan == null || calls == null || calls.isEmpty()) return;
        for (AgentToolCallRecord call : calls) {
            plan.getCompletedSteps().add(AgentPlanStep.builder()
                    .stage(AgentPlanStage.EXECUTE_SKILL)
                    .action(AgentPlanAction.EXECUTE_TOOL)
                    .skillCode(call.getToolName())
                    .summary(call.getResult() != null ? call.getResult().getStatus().name() : "UNKNOWN")
                    .completed(true)
                    .completedAt(LocalDateTime.now())
                    .build());
        }
        AgentToolCallRecord last = calls.get(calls.size() - 1);
        AgentToolResult lastResult = last.getResult();
        if (lastResult != null && lastResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            plan.setCurrentStage(AgentPlanStage.COLLECT_REQUIRED_SLOTS);
            plan.setNextAction(AgentPlanAction.COLLECT_SLOTS);
            plan.setWaitingForUser(true);
        } else {
            plan.setCurrentStage(AgentPlanStage.SUMMARIZE_RESULT);
            plan.setNextAction(AgentPlanAction.RETURN_RESULT);
            plan.setWaitingForUser(false);
        }
        plan.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 记录一个步骤。
     */
    private void recordStep(AgentPlan plan, AgentPlanStage stage, AgentPlanAction action,
                            String skillCode, String summary) {
        plan.getCompletedSteps().add(AgentPlanStep.builder()
                .stage(stage)
                .action(action)
                .skillCode(skillCode)
                .summary(summary)
                .completed(true)
                .completedAt(LocalDateTime.now())
                .build());
    }

    private List<AgentToolParameterField> extractMissingFields(Object data) {
        if (!(data instanceof List<?> rawList)) return List.of();
        List<AgentToolParameterField> fields = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof AgentToolParameterField field) {
                fields.add(field);
            }
        }
        return fields;
    }

    private String goalFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> "create_ticket";
            case QUERY_TICKET -> "query_ticket";
            case TRANSFER_TICKET -> "transfer_ticket";
            case SEARCH_HISTORY -> "search_history";
        };
    }
}
