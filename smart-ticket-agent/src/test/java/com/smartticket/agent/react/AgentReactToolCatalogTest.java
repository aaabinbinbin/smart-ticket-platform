package com.smartticket.agent.react;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartticket.agent.execution.AgentExecutionMode;
import com.smartticket.agent.execution.AgentExecutionPolicy;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentReactToolCatalog} 单元测试。
 *
 * <p>该测试只验证 P5 新增的只读工具暴露边界：只读 ReAct 只能看到 metadata 标记为 readOnly 的工具，
 * 写工具即使出现在 policy.allowedSkills 中也必须被过滤掉。</p>
 */
class AgentReactToolCatalogTest {

    @Test
    void buildToolsShouldOnlyExposeReadOnlyTools() {
        AgentReactToolCatalog catalog = new AgentReactToolCatalog();
        AgentSkill querySkill = skill("queryTicket", AgentIntent.QUERY_TICKET, true, ToolRiskLevel.READ_ONLY);
        AgentSkill searchSkill = skill("searchHistory", AgentIntent.SEARCH_HISTORY, true, ToolRiskLevel.READ_ONLY);
        AgentSkill createSkill = skill("createTicket", AgentIntent.CREATE_TICKET, false, ToolRiskLevel.LOW_RISK_WRITE);
        AgentExecutionPolicy policy = AgentExecutionPolicy.builder()
                .mode(AgentExecutionMode.READ_ONLY_REACT)
                .allowedSkills(List.of(querySkill, createSkill, searchSkill))
                .timeout(Duration.ofSeconds(30))
                .build();

        Object[] tools = catalog.buildTools(policy);
        List<String> toolNames = catalog.allowedToolNames(policy);

        assertEquals(2, tools.length);
        assertSame(querySkill.tool(), tools[0]);
        assertSame(searchSkill.tool(), tools[1]);
        assertEquals(List.of("queryTicket", "searchHistory"), toolNames);
    }

    @Test
    void canExposeShouldRejectWriteSkill() {
        AgentReactToolCatalog catalog = new AgentReactToolCatalog();
        AgentSkill transferSkill = skill("transferTicket", AgentIntent.TRANSFER_TICKET, false, ToolRiskLevel.HIGH_RISK_WRITE);

        assertFalse(catalog.canExpose(transferSkill));
        assertTrue(catalog.canExpose(skill("queryTicket", AgentIntent.QUERY_TICKET, true, ToolRiskLevel.READ_ONLY)));
    }

    private AgentSkill skill(String toolName, AgentIntent intent, boolean readOnly, ToolRiskLevel riskLevel) {
        AgentSkill skill = mock(AgentSkill.class);
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(toolName);
        when(tool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name(toolName)
                .readOnly(readOnly)
                .riskLevel(riskLevel)
                .build());
        when(skill.tool()).thenReturn(tool);
        when(skill.supportedIntents()).thenReturn(List.of(intent));
        when(skill.supports(intent)).thenReturn(true);
        return skill;
    }
}
