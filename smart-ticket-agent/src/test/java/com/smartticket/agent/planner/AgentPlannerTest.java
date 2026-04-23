package com.smartticket.agent.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentPlannerTest {

    @Test
    void buildPlanShouldWaitForUserWhenRouteConfidenceIsLow() {
        AgentPlanner planner = new AgentPlanner(registry(skill(AgentIntent.QUERY_TICKET, ToolRiskLevel.READ_ONLY, true)));

        AgentPlan plan = planner.buildOrLoadPlan(AgentSessionContext.builder().build(), IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.25d)
                .reason("意图不明确")
                .build());

        assertEquals(AgentPlanStage.WAIT_USER, plan.getCurrentStage());
        assertEquals(AgentPlanAction.CLARIFY_INTENT, plan.getNextAction());
        assertTrue(plan.isWaitingForUser());
    }

    @Test
    void buildPlanShouldExposeCreateRequiredSlots() {
        AgentPlanner planner = new AgentPlanner(registry(skill(AgentIntent.CREATE_TICKET, ToolRiskLevel.LOW_RISK_WRITE, true)));

        AgentPlan plan = planner.buildOrLoadPlan(AgentSessionContext.builder().build(), IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.90d)
                .reason("创建工单")
                .build());

        assertEquals("create-ticket", plan.getNextSkillCode());
        assertEquals(List.of(AgentToolParameterField.TITLE, AgentToolParameterField.DESCRIPTION), plan.getRequiredSlots());
        assertFalse(plan.isWaitingForUser());
    }

    private SkillRegistry registry(AgentSkill skill) {
        return new SkillRegistry(List.of(skill));
    }

    private AgentSkill skill(AgentIntent intent, ToolRiskLevel riskLevel, boolean autoExecute) {
        AgentSkill skill = mock(AgentSkill.class);
        AgentTool tool = mock(AgentTool.class);
        when(skill.skillCode()).thenReturn(switch (intent) {
            case CREATE_TICKET -> "create-ticket";
            case QUERY_TICKET -> "query-ticket";
            case TRANSFER_TICKET -> "transfer-ticket";
            case SEARCH_HISTORY -> "search-history";
        });
        when(skill.supportedIntents()).thenReturn(List.of(intent));
        when(skill.supports(intent)).thenReturn(true);
        when(skill.riskLevel()).thenReturn(riskLevel);
        when(skill.canAutoExecute()).thenReturn(autoExecute);
        when(skill.inputSchema()).thenReturn(List.of(AgentToolParameterField.TITLE, AgentToolParameterField.DESCRIPTION));
        when(skill.tool()).thenReturn(tool);
        return skill;
    }
}
