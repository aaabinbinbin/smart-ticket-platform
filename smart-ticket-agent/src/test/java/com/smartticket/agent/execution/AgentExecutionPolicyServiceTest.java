package com.smartticket.agent.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.biz.model.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AgentExecutionPolicyService 的 P4 单元测试。
 *
 * <p>这些测试只验证执行策略解析，不覆盖具体工具执行。重点保护 mode、allowedTools、
 * confirmation 和 budget 是否按 intent 与风险等级稳定输出。</p>
 */
class AgentExecutionPolicyServiceTest {

    @Test
    void resolveShouldReturnClarificationPolicyWhenConfidenceIsLow() {
        AgentExecutionPolicyService service = new AgentExecutionPolicyService(new SkillRegistry(List.of()));

        AgentExecutionPolicy policy = service.resolve(currentUser(), IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.20d)
                .reason("需要澄清")
                .build());

        assertEquals(AgentExecutionMode.CLARIFICATION, policy.getMode());
        assertFalse(policy.isAllowReact());
        assertEquals(0, policy.getMaxToolCalls());
    }

    @Test
    void resolveShouldUseReadOnlyReactForQueryTicket() {
        AgentSkill querySkill = skill("queryTicket", AgentIntent.QUERY_TICKET, ToolRiskLevel.READ_ONLY, true, false);
        AgentExecutionPolicyService service = new AgentExecutionPolicyService(new SkillRegistry(List.of(querySkill)));

        AgentExecutionPolicy policy = service.resolve(currentUser(), IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.95d)
                .reason("查询")
                .build());

        assertEquals(AgentExecutionMode.READ_ONLY_REACT, policy.getMode());
        assertTrue(policy.isAllowReact());
        assertEquals(List.of("queryTicket"), policy.getAllowedToolNames());
        assertEquals(ToolRiskLevel.READ_ONLY, policy.getMaxRiskLevel());
        assertEquals(1, policy.getMaxLlmCalls());
    }

    @Test
    void resolveShouldSafeFailWhenUserHasNoRequiredPermission() {
        AgentSkill querySkill = skill(
                "queryTicket",
                AgentIntent.QUERY_TICKET,
                ToolRiskLevel.READ_ONLY,
                true,
                false,
                List.of("AGENT_QUERY")
        );
        AgentExecutionPolicyService service = new AgentExecutionPolicyService(new SkillRegistry(List.of(querySkill)));

        AgentExecutionPolicy policy = service.resolve(currentUser(), IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.95d)
                .reason("查询")
                .build());

        assertSafeFailure(policy, ToolRiskLevel.READ_ONLY);
    }

    @Test
    void resolveShouldSafeFailWhenSkillRiskExceedsPolicyLimit() {
        AgentSkill riskyCreateSkill = skill(
                "createTicket",
                AgentIntent.CREATE_TICKET,
                ToolRiskLevel.HIGH_RISK_WRITE,
                true,
                false
        );
        AgentExecutionPolicyService service = new AgentExecutionPolicyService(new SkillRegistry(List.of(riskyCreateSkill)));

        AgentExecutionPolicy policy = service.resolve(currentUser(), IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.95d)
                .reason("创建")
                .build());

        assertSafeFailure(policy, ToolRiskLevel.LOW_RISK_WRITE);
    }

    @Test
    void resolveShouldSafeFailWhenNoAvailableSkillExists() {
        AgentExecutionPolicyService service = new AgentExecutionPolicyService(new SkillRegistry(List.of()));

        AgentExecutionPolicy policy = service.resolve(currentUser(), IntentRoute.builder()
                .intent(AgentIntent.SEARCH_HISTORY)
                .confidence(0.96d)
                .reason("检索")
                .build());

        assertSafeFailure(policy, ToolRiskLevel.READ_ONLY);
    }

    @Test
    void resolveShouldUseWriteCommandExecuteForCreateTicket() {
        AgentSkill createSkill = skill("createTicket", AgentIntent.CREATE_TICKET, ToolRiskLevel.LOW_RISK_WRITE, true, false);
        AgentExecutionPolicyService service = new AgentExecutionPolicyService(new SkillRegistry(List.of(createSkill)));

        AgentExecutionPolicy policy = service.resolve(currentUser(), IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.93d)
                .reason("创建")
                .build());

        assertEquals(AgentExecutionMode.WRITE_COMMAND_EXECUTE, policy.getMode());
        assertFalse(policy.isAllowReact());
        assertTrue(policy.isAllowAutoExecute());
        assertFalse(policy.isRequireConfirmation());
        assertEquals(List.of("createTicket"), policy.getAllowedToolNames());
        assertEquals(1, policy.getMaxToolCalls());
    }

    @Test
    void resolveShouldRequireConfirmationForTransferTicket() {
        AgentSkill transferSkill = skill("transferTicket", AgentIntent.TRANSFER_TICKET, ToolRiskLevel.HIGH_RISK_WRITE, false, true);
        AgentExecutionPolicyService service = new AgentExecutionPolicyService(new SkillRegistry(List.of(transferSkill)));

        AgentExecutionPolicy policy = service.resolve(currentUser(), IntentRoute.builder()
                .intent(AgentIntent.TRANSFER_TICKET)
                .confidence(0.94d)
                .reason("转派")
                .build());

        assertEquals(AgentExecutionMode.HIGH_RISK_CONFIRMATION, policy.getMode());
        assertFalse(policy.isAllowReact());
        assertFalse(policy.isAllowAutoExecute());
        assertTrue(policy.isRequireConfirmation());
        assertEquals(List.of("transferTicket"), policy.getAllowedToolNames());
        assertEquals(ToolRiskLevel.HIGH_RISK_WRITE, policy.getMaxRiskLevel());
    }

    private AgentSkill skill(
            String toolName,
            AgentIntent intent,
            ToolRiskLevel riskLevel,
            boolean canAutoExecute,
            boolean requireConfirmation
    ) {
        return skill(toolName, intent, riskLevel, canAutoExecute, requireConfirmation, List.of());
    }

    private AgentSkill skill(
            String toolName,
            AgentIntent intent,
            ToolRiskLevel riskLevel,
            boolean canAutoExecute,
            boolean requireConfirmation,
            List<String> requiredPermissions
    ) {
        AgentSkill skill = mock(AgentSkill.class);
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(toolName);
        when(tool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name(toolName)
                .riskLevel(riskLevel)
                .readOnly(riskLevel == ToolRiskLevel.READ_ONLY)
                .requireConfirmation(requireConfirmation)
                .build());
        when(skill.skillCode()).thenReturn(toolName + "-skill");
        when(skill.tool()).thenReturn(tool);
        when(skill.supportedIntents()).thenReturn(List.of(intent));
        when(skill.supports(intent)).thenReturn(true);
        when(skill.requiredPermissions()).thenReturn(requiredPermissions);
        when(skill.riskLevel()).thenReturn(riskLevel);
        when(skill.canAutoExecute()).thenReturn(canAutoExecute);
        return skill;
    }

    private void assertSafeFailure(AgentExecutionPolicy policy, ToolRiskLevel maxRiskLevel) {
        assertEquals(AgentExecutionMode.SAFE_FAILURE, policy.getMode());
        assertTrue(policy.getAllowedSkills().isEmpty());
        assertTrue(policy.getAllowedToolNames().isEmpty());
        assertFalse(policy.isAllowReact());
        assertFalse(policy.isAllowAutoExecute());
        assertFalse(policy.isRequireConfirmation());
        assertEquals(maxRiskLevel, policy.getMaxRiskLevel());
        assertEquals(0, policy.getMaxToolCalls());
    }

    private CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
