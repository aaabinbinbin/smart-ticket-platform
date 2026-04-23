package com.smartticket.agent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillRegistryTest {

    @Test
    void findAvailableShouldFilterByIntentPermissionAndRisk() {
        AgentSkill querySkill = skill("query-ticket", AgentIntent.QUERY_TICKET, ToolRiskLevel.READ_ONLY, List.of("ticket:read"));
        AgentSkill transferSkill = skill("transfer-ticket", AgentIntent.TRANSFER_TICKET, ToolRiskLevel.HIGH_RISK_WRITE, List.of("ticket:transfer"));
        SkillRegistry registry = new SkillRegistry(List.of(querySkill, transferSkill));

        List<AgentSkill> available = registry.findAvailable(
                AgentIntent.QUERY_TICKET,
                List.of("ticket:read"),
                ToolRiskLevel.READ_ONLY
        );

        assertEquals(1, available.size());
        assertEquals("query-ticket", available.get(0).skillCode());
    }

    private AgentSkill skill(String code, AgentIntent intent, ToolRiskLevel riskLevel, List<String> permissions) {
        AgentSkill skill = mock(AgentSkill.class);
        when(skill.skillCode()).thenReturn(code);
        when(skill.supports(intent)).thenReturn(true);
        when(skill.supportedIntents()).thenReturn(List.of(intent));
        when(skill.riskLevel()).thenReturn(riskLevel);
        when(skill.requiredPermissions()).thenReturn(permissions);
        return skill;
    }
}
