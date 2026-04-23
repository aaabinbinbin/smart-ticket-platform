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

@Service
public class AgentPlanner {
    private final SkillRegistry skillRegistry;

    public AgentPlanner(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public AgentPlan buildOrLoadPlan(AgentSessionContext context, IntentRoute route) {
        AgentPlan existing = context == null ? null : context.getPlanState();
        if (existing != null && existing.getIntent() == route.getIntent() && existing.isWaitingForUser()) {
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
            plan.setCurrentStage(AgentPlanStage.WAIT_USER);
            plan.setNextAction(AgentPlanAction.CLARIFY_INTENT);
            plan.setWaitingForUser(true);
        } else if (skill.riskLevel() == ToolRiskLevel.HIGH_RISK_WRITE && !skill.canAutoExecute()) {
            plan.setCurrentStage(AgentPlanStage.WAIT_USER);
            plan.setNextAction(AgentPlanAction.CONFIRM_HIGH_RISK);
            plan.setWaitingForUser(true);
        }
        return plan;
    }

    public void markClarify(AgentPlan plan, String summary) {
        complete(plan, AgentPlanStage.ROUTE_INTENT, AgentPlanAction.CLARIFY_INTENT, null, summary);
        plan.setCurrentStage(AgentPlanStage.WAIT_USER);
        plan.setNextAction(AgentPlanAction.CLARIFY_INTENT);
        plan.setWaitingForUser(true);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    public void beforeExecute(AgentPlan plan) {
        if (plan == null) {
            return;
        }
        plan.setCurrentStage(AgentPlanStage.EXECUTE_SKILL);
        plan.setNextAction(AgentPlanAction.EXECUTE_TOOL);
        plan.setWaitingForUser(false);
        plan.setUpdatedAt(LocalDateTime.now());
    }

    public void afterTool(AgentPlan plan, AgentToolResult result) {
        if (plan == null || result == null) {
            return;
        }
        complete(plan, AgentPlanStage.EXECUTE_SKILL, AgentPlanAction.EXECUTE_TOOL, result.getToolName(), result.getStatus().name());
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

    private String goalFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> "create_ticket";
            case QUERY_TICKET -> "query_ticket";
            case TRANSFER_TICKET -> "transfer_ticket";
            case SEARCH_HISTORY -> "search_history";
        };
    }

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
