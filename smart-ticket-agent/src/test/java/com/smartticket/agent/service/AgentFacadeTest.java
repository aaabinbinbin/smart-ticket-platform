package com.smartticket.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.memory.AgentMemoryService;
import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanAction;
import com.smartticket.agent.planner.AgentPlanStage;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.prompt.PromptTemplateService;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.ticket.CreateTicketTool;
import com.smartticket.agent.tool.ticket.QueryTicketTool;
import com.smartticket.agent.tool.ticket.SearchHistoryTool;
import com.smartticket.agent.tool.ticket.TransferTicketTool;
import com.smartticket.agent.trace.AgentTraceContext;
import com.smartticket.agent.trace.AgentTraceService;
import com.smartticket.biz.model.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AgentFacadeTest {

    @Test
    void chatShouldClarifyWhenRouteConfidenceIsLow() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().activeTicketId(1001L).build();
        when(fixture.sessionService.load("s-clarify")).thenReturn(context);
        when(fixture.intentRouter.route("帮我处理一下", context)).thenReturn(IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.25d)
                .reason("需要澄清")
                .build());

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-clarify", "帮我处理一下");

        assertEquals("QUERY_TICKET", result.getIntent());
        assertEquals("我暂时无法判断你的目标。请明确说明你是想查询工单、创建工单、转派工单，还是检索历史案例。", result.getReply());
        assertNull(result.getResult());
        verifyNoInteractions(
                fixture.executionGuard,
                fixture.parameterExtractor,
                fixture.queryTicketTool,
                fixture.createTicketTool,
                fixture.transferTicketTool,
                fixture.searchHistoryTool
        );
    }

    @Test
    void chatShouldContinuePendingCreateAndKeepDraftWhenStillMissingFields() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder()
                .pendingAction(AgentPendingAction.builder()
                        .pendingIntent(AgentIntent.CREATE_TICKET)
                        .pendingToolName("createTicket")
                        .pendingParameters(AgentToolParameters.builder()
                                .title("测试环境登录失败")
                                .description("测试环境登录报错 500")
                                .build())
                        .awaitingFields(List.of(AgentToolParameterField.CATEGORY, AgentToolParameterField.PRIORITY))
                        .build())
                .build();
        when(fixture.sessionService.load("s-create")).thenReturn(context);
        when(fixture.parameterExtractor.extract("系统类", context)).thenReturn(AgentToolParameters.builder()
                .category(com.smartticket.domain.enums.TicketCategoryEnum.SYSTEM)
                .build());
        when(fixture.createTicketTool.execute(argThat(request ->
                request.getParameters().getCategory() == com.smartticket.domain.enums.TicketCategoryEnum.SYSTEM
                        && request.getParameters().getPriority() == null
                        && "测试环境登录失败".equals(request.getParameters().getTitle())
        ))).thenReturn(AgentToolResult.builder()
                .status(AgentToolStatus.NEED_MORE_INFO)
                .toolName("createTicket")
                .data(List.of(AgentToolParameterField.PRIORITY))
                .build());

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-create", "系统类");

        assertEquals("CREATE_TICKET", result.getIntent());
        assertEquals("请补充工单优先级：LOW、MEDIUM、HIGH、URGENT。", result.getReply());
        assertNotNull(result.getContext().getPendingAction());
        assertEquals(AgentIntent.CREATE_TICKET, result.getContext().getPendingAction().getPendingIntent());
        assertEquals(com.smartticket.domain.enums.TicketCategoryEnum.SYSTEM,
                result.getContext().getPendingAction().getPendingParameters().getCategory());
        verify(fixture.sessionService).updateAfterTool(eq("s-create"), eq(context), any(), eq("系统类"), any());
    }

    @Test
    void chatShouldContinuePendingCreateAndClearDraftWhenCompleted() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder()
                .pendingAction(AgentPendingAction.builder()
                        .pendingIntent(AgentIntent.CREATE_TICKET)
                        .pendingToolName("createTicket")
                        .pendingParameters(AgentToolParameters.builder()
                                .title("测试环境登录失败")
                                .description("测试环境登录报错 500")
                                .category(com.smartticket.domain.enums.TicketCategoryEnum.SYSTEM)
                                .build())
                        .awaitingFields(List.of(AgentToolParameterField.PRIORITY))
                        .build())
                .build();
        when(fixture.sessionService.load("s-create-success")).thenReturn(context);
        when(fixture.parameterExtractor.extract("高优先级", context)).thenReturn(AgentToolParameters.builder()
                .priority(com.smartticket.domain.enums.TicketPriorityEnum.HIGH)
                .build());
        when(fixture.createTicketTool.execute(any())).thenReturn(AgentToolResult.builder()
                .status(AgentToolStatus.SUCCESS)
                .toolName("createTicket")
                .reply("已创建工单。")
                .build());

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-create-success", "高优先级");

        assertEquals("CREATE_TICKET", result.getIntent());
        assertEquals("已创建工单。", result.getReply());
        assertNull(result.getContext().getPendingAction());
    }

    @Test
    void chatShouldRequireConfirmationBeforeHighRiskTransfer() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().activeTicketId(1001L).build();
        AgentSkill skill = mock(AgentSkill.class);
        AgentToolParameters parameters = AgentToolParameters.builder()
                .ticketId(1001L)
                .assigneeId(3L)
                .build();
        when(fixture.sessionService.load("s-transfer")).thenReturn(context);
        when(fixture.intentRouter.route("transfer", context)).thenReturn(IntentRoute.builder()
                .intent(AgentIntent.TRANSFER_TICKET)
                .confidence(0.90d)
                .reason("transfer")
                .build());
        when(fixture.skillRegistry.requireByIntent(AgentIntent.TRANSFER_TICKET)).thenReturn(skill);
        when(skill.tool()).thenReturn(fixture.transferTicketTool);
        when(fixture.parameterExtractor.extract("transfer", context)).thenReturn(parameters);
        when(fixture.executionGuard.check(any(), eq("transfer"), eq(context), any(), any()))
                .thenReturn(AgentExecutionDecision.needConfirmation(fixture.transferTicketTool, "risk"));

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-transfer", "transfer");

        assertEquals("TRANSFER_TICKET", result.getIntent());
        assertNotNull(result.getContext().getPendingAction());
        assertEquals(AgentIntent.TRANSFER_TICKET, result.getContext().getPendingAction().getPendingIntent());
        assertEquals(1001L, result.getContext().getPendingAction().getPendingParameters().getTicketId());
        assertEquals(3L, result.getContext().getPendingAction().getPendingParameters().getAssigneeId());
        assertEquals(true, result.getContext().getPendingAction().isAwaitingConfirmation());
        verify(fixture.transferTicketTool, never()).execute(any());
    }

    @Test
    void chatShouldExecutePendingHighRiskTransferAfterConfirmation() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder()
                .pendingAction(AgentPendingAction.builder()
                        .pendingIntent(AgentIntent.TRANSFER_TICKET)
                        .pendingToolName("transferTicket")
                        .pendingParameters(AgentToolParameters.builder()
                                .ticketId(1001L)
                                .assigneeId(3L)
                                .build())
                        .awaitingConfirmation(true)
                        .confirmationSummary("confirm transfer")
                        .build())
                .build();
        when(fixture.sessionService.load("s-confirm")).thenReturn(context);
        when(fixture.transferTicketTool.execute(argThat(request ->
                request.getParameters().getTicketId().equals(1001L)
                        && request.getParameters().getAssigneeId().equals(3L)
        ))).thenReturn(AgentToolResult.builder()
                .status(AgentToolStatus.SUCCESS)
                .toolName("transferTicket")
                .reply("transferred")
                .activeTicketId(1001L)
                .activeAssigneeId(3L)
                .build());

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-confirm", "confirm");

        assertEquals("TRANSFER_TICKET", result.getIntent());
        assertEquals("transferred", result.getReply());
        assertNull(result.getContext().getPendingAction());
    }

    private TestFixture fixture() {
        ObjectProvider<?> chatClientProvider = mock(ObjectProvider.class);
        IntentRouter intentRouter = mock(IntentRouter.class);
        AgentSessionService sessionService = mock(AgentSessionService.class);
        AgentExecutionGuard executionGuard = mock(AgentExecutionGuard.class);
        AgentPlanner agentPlanner = mock(AgentPlanner.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentMemoryService memoryService = mock(AgentMemoryService.class);
        AgentTraceService traceService = mock(AgentTraceService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        AgentToolParameterExtractor parameterExtractor = mock(AgentToolParameterExtractor.class);
        QueryTicketTool queryTicketTool = mock(QueryTicketTool.class);
        CreateTicketTool createTicketTool = mock(CreateTicketTool.class);
        TransferTicketTool transferTicketTool = mock(TransferTicketTool.class);
        SearchHistoryTool searchHistoryTool = mock(SearchHistoryTool.class);
        when(createTicketTool.name()).thenReturn("createTicket");
        when(transferTicketTool.name()).thenReturn("transferTicket");
        when(queryTicketTool.name()).thenReturn("queryTicket");
        when(searchHistoryTool.name()).thenReturn("searchHistory");
        when(traceService.start(any(), any(), any())).thenReturn(new AgentTraceContext("trace-1", "session", 1L, "message"));
        when(agentPlanner.buildOrLoadPlan(any(), any())).thenReturn(AgentPlan.builder()
                .goal("create_ticket")
                .intent(AgentIntent.CREATE_TICKET)
                .currentStage(AgentPlanStage.EXECUTE_SKILL)
                .nextAction(AgentPlanAction.EXECUTE_TOOL)
                .nextSkillCode("create-ticket")
                .riskLevel(ToolRiskLevel.LOW_RISK_WRITE)
                .build());
        AgentFacade agentFacade = new AgentFacade(
                (ObjectProvider) chatClientProvider,
                false,
                intentRouter,
                sessionService,
                executionGuard,
                agentPlanner,
                skillRegistry,
                memoryService,
                traceService,
                promptTemplateService,
                parameterExtractor,
                queryTicketTool,
                createTicketTool,
                transferTicketTool,
                searchHistoryTool
        );
        return new TestFixture(
                agentFacade,
                intentRouter,
                sessionService,
                executionGuard,
                agentPlanner,
                skillRegistry,
                memoryService,
                traceService,
                promptTemplateService,
                parameterExtractor,
                queryTicketTool,
                createTicketTool,
                transferTicketTool,
                searchHistoryTool
        );
    }

    private record TestFixture(
            AgentFacade agentFacade,
            IntentRouter intentRouter,
            AgentSessionService sessionService,
            AgentExecutionGuard executionGuard,
            AgentPlanner agentPlanner,
            SkillRegistry skillRegistry,
            AgentMemoryService memoryService,
            AgentTraceService traceService,
            PromptTemplateService promptTemplateService,
            AgentToolParameterExtractor parameterExtractor,
            QueryTicketTool queryTicketTool,
            CreateTicketTool createTicketTool,
            TransferTicketTool transferTicketTool,
            SearchHistoryTool searchHistoryTool
    ) {
    }

    private CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
