package com.smartticket.agent.tool.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.resilience.AgentTurnBudgetService;
import com.smartticket.agent.service.AgentSessionService;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/**
 * SpringAiToolSupport 的 P0 安全边界测试。
 *
 * <p>当前主链尚未拆出正式的工具白名单，因此这里先用最小补丁保护一个明确边界：
 * 当原始路由属于 QUERY_TICKET 或 SEARCH_HISTORY 这类只读意图时，Spring AI 不得借由 Tool Calling
 * 触发写工具。这样可以在不重构主链的前提下，为后续 P4/P5 的执行策略与工具白名单提供回归护栏。</p>
 */
class SpringAiToolSupportTest {

    @Test
    void executeShouldRejectWriteToolWhenOriginalRouteIsReadOnly() {
        AgentExecutionGuard executionGuard = mock(AgentExecutionGuard.class);
        AgentSessionService sessionService = mock(AgentSessionService.class);
        SpringAiToolSupport support = new SpringAiToolSupport(executionGuard, sessionService, new AgentTurnBudgetService());
        AgentTool writeTool = mock(AgentTool.class);
        ToolContext toolContext = mock(ToolContext.class);
        SpringAiToolCallState state = new SpringAiToolCallState();
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(SpringAiToolSupport.CURRENT_USER_KEY, currentUser());
        contextMap.put(SpringAiToolSupport.SESSION_CONTEXT_KEY, AgentSessionContext.builder().build());
        contextMap.put(SpringAiToolSupport.MESSAGE_KEY, "查一下类似案例");
        contextMap.put(SpringAiToolSupport.ROUTE_KEY, IntentRoute.builder()
                .intent(AgentIntent.SEARCH_HISTORY)
                .confidence(0.93d)
                .reason("命中历史查询关键词")
                .build());
        contextMap.put(SpringAiToolSupport.STATE_KEY, state);
        when(toolContext.getContext()).thenReturn(contextMap);
        when(writeTool.name()).thenReturn("createTicket");
        when(writeTool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name("createTicket")
                .riskLevel(ToolRiskLevel.LOW_RISK_WRITE)
                .readOnly(false)
                .build());

        AgentToolResult result = support.execute(
                writeTool,
                toolContext,
                AgentIntent.CREATE_TICKET,
                AgentToolParameters.builder().title("登录失败").description("请帮我创建").build()
        );

        assertEquals(AgentToolStatus.FAILED, result.getStatus());
        assertTrue(result.getReply().contains("只读推理阶段不允许调用写工具"));
        assertEquals("createTicket", state.getLastCall().getToolName());
        verifyNoInteractions(executionGuard, sessionService);
        verify(writeTool, never()).execute(any());
    }

    @Test
    void executeShouldAllowReadOnlyToolWhenOriginalRouteIsReadOnly() {
        AgentExecutionGuard executionGuard = mock(AgentExecutionGuard.class);
        AgentSessionService sessionService = mock(AgentSessionService.class);
        SpringAiToolSupport support = new SpringAiToolSupport(executionGuard, sessionService, new AgentTurnBudgetService());
        AgentTool readOnlyTool = mock(AgentTool.class);
        ToolContext toolContext = mock(ToolContext.class);
        SpringAiToolCallState state = new SpringAiToolCallState();
        AgentSessionContext sessionContext = AgentSessionContext.builder().activeTicketId(1001L).build();
        AgentToolResult successResult = AgentToolResult.builder()
                .status(AgentToolStatus.SUCCESS)
                .toolName("queryTicket")
                .reply("已查询工单详情。")
                .activeTicketId(1001L)
                .build();
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(SpringAiToolSupport.CURRENT_USER_KEY, currentUser());
        contextMap.put(SpringAiToolSupport.SESSION_CONTEXT_KEY, sessionContext);
        contextMap.put(SpringAiToolSupport.MESSAGE_KEY, "查询工单1001");
        contextMap.put(SpringAiToolSupport.ROUTE_KEY, IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.95d)
                .reason("命中查询关键词")
                .build());
        contextMap.put(SpringAiToolSupport.STATE_KEY, state);
        when(toolContext.getContext()).thenReturn(contextMap);
        when(readOnlyTool.name()).thenReturn("queryTicket");
        when(readOnlyTool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name("queryTicket")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .readOnly(true)
                .build());
        when(executionGuard.check(any(), any(), any(), any(), any()))
                .thenReturn(AgentExecutionDecision.allow(readOnlyTool));
        when(readOnlyTool.execute(any())).thenReturn(successResult);

        AgentToolResult result = support.execute(
                readOnlyTool,
                toolContext,
                AgentIntent.QUERY_TICKET,
                AgentToolParameters.builder().ticketId(1001L).build()
        );

        assertEquals(AgentToolStatus.SUCCESS, result.getStatus());
        assertEquals("queryTicket", state.getLastCall().getToolName());
        verify(sessionService).resolveReferences(any(), any(), any());
        verify(executionGuard).check(any(), any(), any(), any(), any());
        verify(readOnlyTool).execute(any());
    }

    @Test
    void executeShouldRejectToolOutsideAllowedPolicyList() {
        AgentExecutionGuard executionGuard = mock(AgentExecutionGuard.class);
        AgentSessionService sessionService = mock(AgentSessionService.class);
        SpringAiToolSupport support = new SpringAiToolSupport(executionGuard, sessionService, new AgentTurnBudgetService());
        AgentTool readOnlyTool = mock(AgentTool.class);
        ToolContext toolContext = mock(ToolContext.class);
        SpringAiToolCallState state = new SpringAiToolCallState();
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(SpringAiToolSupport.CURRENT_USER_KEY, currentUser());
        contextMap.put(SpringAiToolSupport.SESSION_CONTEXT_KEY, AgentSessionContext.builder().build());
        contextMap.put(SpringAiToolSupport.MESSAGE_KEY, "查一下案例");
        contextMap.put(SpringAiToolSupport.ROUTE_KEY, IntentRoute.builder()
                .intent(AgentIntent.SEARCH_HISTORY)
                .confidence(0.92d)
                .reason("检索案例")
                .build());
        contextMap.put(SpringAiToolSupport.STATE_KEY, state);
        contextMap.put(SpringAiToolSupport.ALLOWED_TOOL_NAMES_KEY, java.util.List.of("searchHistory"));
        when(toolContext.getContext()).thenReturn(contextMap);
        when(readOnlyTool.name()).thenReturn("queryTicket");
        when(readOnlyTool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name("queryTicket")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .readOnly(true)
                .build());

        AgentToolResult result = support.execute(
                readOnlyTool,
                toolContext,
                AgentIntent.QUERY_TICKET,
                AgentToolParameters.builder().ticketId(1001L).build()
        );

        assertEquals(AgentToolStatus.FAILED, result.getStatus());
        assertTrue(result.getReply().contains("未授权该工具"));
        verifyNoInteractions(executionGuard, sessionService);
        verify(readOnlyTool, never()).execute(any());
    }

    private CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .build();
    }
}
