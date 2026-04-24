package com.smartticket.agent.react;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.agent.execution.AgentExecutionMode;
import com.smartticket.agent.execution.AgentExecutionPolicy;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanAction;
import com.smartticket.agent.planner.AgentPlanStage;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.prompt.PromptTemplateService;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.trace.AgentTraceContext;
import com.smartticket.agent.trace.AgentTraceService;
import com.smartticket.biz.model.CurrentUser;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

/**
 * {@link ReadOnlyReactExecutor} 单元测试。
 *
 * <p>该测试聚焦 P5 新增执行器的两个边界：没有只读工具时必须直接返回 empty，
 * 有只读工具时可以正常产出只读 ReAct 摘要，但不会在执行器内部提交 session/memory/pendingAction。</p>
 */
class ReadOnlyReactExecutorTest {

    @Test
    void executeShouldReturnEmptyWhenPolicyHasNoReadableTools() {
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        ReadOnlyReactExecutor executor = new ReadOnlyReactExecutor(
                provider(chatClient),
                mock(AgentPlanner.class),
                promptTemplateService(),
                mock(AgentTraceService.class),
                new AgentReactToolCatalog()
        );
        AgentExecutionPolicy policy = AgentExecutionPolicy.builder()
                .mode(AgentExecutionMode.READ_ONLY_REACT)
                .allowedSkills(List.of(skill("createTicket", AgentIntent.CREATE_TICKET, false, ToolRiskLevel.LOW_RISK_WRITE)))
                .timeout(Duration.ofSeconds(30))
                .build();

        var result = executor.execute(
                currentUser(),
                "查询工单1001",
                AgentSessionContext.builder().build(),
                route(AgentIntent.QUERY_TICKET),
                plan(),
                policy,
                new AgentTraceContext("trace-1", "session", 1L, "msg")
        );

        assertTrue(result.isEmpty());
        verify(chatClient, never()).prompt();
    }

    @Test
    void executeShouldReturnCompletedSummaryWhenStreamingSucceeds() {
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        AgentPlanner planner = mock(AgentPlanner.class);
        AgentTraceService traceService = mock(AgentTraceService.class);
        ReadOnlyReactExecutor executor = new ReadOnlyReactExecutor(
                provider(chatClient),
                planner,
                promptTemplateService(),
                traceService,
                new AgentReactToolCatalog()
        );
        ChatResponse response = mock(ChatResponse.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        AgentExecutionPolicy policy = AgentExecutionPolicy.builder()
                .mode(AgentExecutionMode.READ_ONLY_REACT)
                .allowedSkills(List.of(skill("queryTicket", AgentIntent.QUERY_TICKET, true, ToolRiskLevel.READ_ONLY)))
                .timeout(Duration.ofSeconds(30))
                .build();
        when(response.getResult().getOutput().getText()).thenReturn("这是模型的只读总结。");
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .tools(any(Object[].class))
                .toolContext(anyMap())
                .stream()
                .chatResponse())
                .thenReturn(Flux.just(response));

        var result = executor.execute(
                currentUser(),
                "查询工单1001",
                AgentSessionContext.builder().activeTicketId(1001L).build(),
                route(AgentIntent.QUERY_TICKET),
                plan(),
                policy,
                new AgentTraceContext("trace-1", "session", 1L, "msg")
        );

        assertTrue(result.isPresent());
        assertEquals(AgentExecutionMode.READ_ONLY_REACT, result.get().summary().getMode());
        assertEquals("这是模型的只读总结。", result.get().summary().getModelReply());
        assertFalse(result.get().summary().isFallbackUsed());
        assertTrue(result.get().toolCalls().isEmpty());
        verify(planner).beforeExecute(any());
        verify(traceService).recordReasoning(any(), any());
    }

    private ObjectProvider<ChatClient> provider(ChatClient chatClient) {
        ObjectProvider<ChatClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatClient);
        return provider;
    }

    private PromptTemplateService promptTemplateService() {
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        when(promptTemplateService.content(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        return promptTemplateService;
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

    private IntentRoute route(AgentIntent intent) {
        return IntentRoute.builder()
                .intent(intent)
                .confidence(0.95d)
                .reason("命中查询关键词")
                .build();
    }

    private AgentPlan plan() {
        return AgentPlan.builder()
                .goal("query_ticket")
                .intent(AgentIntent.QUERY_TICKET)
                .currentStage(AgentPlanStage.EXECUTE_SKILL)
                .nextAction(AgentPlanAction.EXECUTE_TOOL)
                .nextSkillCode("query-ticket")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .build();
    }

    private CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
