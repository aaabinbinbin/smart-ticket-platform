package com.smartticket.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartticket.agent.command.CreateTicketCommandHandler;
import com.smartticket.agent.command.TransferTicketCommandHandler;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionPolicyService;
import com.smartticket.agent.execution.DeterministicCommandExecutor;
import com.smartticket.agent.execution.PendingActionCoordinator;
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
import com.smartticket.agent.react.AgentReactToolCatalog;
import com.smartticket.agent.react.ReadOnlyReactExecutor;
import com.smartticket.agent.resilience.AgentDegradePolicyService;
import com.smartticket.agent.resilience.AgentRateLimitService;
import com.smartticket.agent.resilience.AgentSessionLockService;
import com.smartticket.agent.resilience.AgentTurnBudgetService;
import com.smartticket.agent.reply.AgentReplyRenderer;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.stream.AgentEventSink;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.core.AgentToolMetadata;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

/**
 * AgentFacade 的 P0 主链回归测试。
 *
 * <p>这些测试以当前真实实现为准，优先保护 /api/agent/chat 经过 AgentFacade 时的既有行为，
 * 避免后续 P1-P5 重构时把查询、历史检索、补参、确认和取消分支改坏。</p>
 */
class AgentFacadeTest {

    @Test
    void chatShouldQueryTicketThroughDeterministicFallback() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().build();
        AgentToolResult toolResult = AgentToolResult.builder()
                .status(AgentToolStatus.SUCCESS)
                .toolName("queryTicket")
                .reply("已查询工单详情。")
                .data("ticket-detail")
                .activeTicketId(1001L)
                .build();
        when(fixture.sessionService.load("s-query")).thenReturn(context);
        when(fixture.intentRouter.route("查询工单1001", context)).thenReturn(IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.92d)
                .reason("命中查询关键词")
                .build());
        when(fixture.parameterExtractor.extract("查询工单1001", context)).thenReturn(AgentToolParameters.builder()
                .ticketId(1001L)
                .build());
        when(fixture.executionGuard.check(any(), eq("查询工单1001"), eq(context), any(), any()))
                .thenReturn(AgentExecutionDecision.allow(fixture.queryTicketTool));
        when(fixture.queryTicketTool.execute(any())).thenReturn(toolResult);

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-query", "查询工单1001");

        assertEquals("QUERY_TICKET", result.getIntent());
        assertEquals("已查询工单详情。", result.getReply());
        assertEquals("ticket-detail", result.getResult());
        verify(fixture.sessionService).updateAfterTool(eq("s-query"), eq(context), any(), eq("查询工单1001"), eq(toolResult));
        verify(fixture.queryTicketTool).execute(any());
        verify(fixture.searchHistoryTool, never()).execute(any());
        verify(fixture.createTicketTool, never()).execute(any());
        verify(fixture.transferTicketTool, never()).execute(any());
    }

    @Test
    void chatShouldSearchHistoryThroughDeterministicFallback() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().recentMessages(List.of("QUERY_TICKET: 查询工单1001")).build();
        AgentToolResult toolResult = AgentToolResult.builder()
                .status(AgentToolStatus.SUCCESS)
                .toolName("searchHistory")
                .reply("已检索到相似历史经验，结果仅供参考，不作为当前工单事实裁决。")
                .data("history-result")
                .build();
        when(fixture.sessionService.load("s-history")).thenReturn(context);
        when(fixture.intentRouter.route("查一下类似历史案例", context)).thenReturn(IntentRoute.builder()
                .intent(AgentIntent.SEARCH_HISTORY)
                .confidence(0.93d)
                .reason("命中历史查询关键词")
                .build());
        when(fixture.parameterExtractor.extract("查一下类似历史案例", context)).thenReturn(AgentToolParameters.builder()
                .description("查一下类似历史案例")
                .build());
        when(fixture.executionGuard.check(any(), eq("查一下类似历史案例"), eq(context), any(), any()))
                .thenReturn(AgentExecutionDecision.allow(fixture.searchHistoryTool));
        when(fixture.searchHistoryTool.execute(any())).thenReturn(toolResult);

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-history", "查一下类似历史案例");

        assertEquals("SEARCH_HISTORY", result.getIntent());
        assertEquals("已检索到相似历史经验，结果仅供参考，不作为当前工单事实裁决。", result.getReply());
        assertEquals("history-result", result.getResult());
        verify(fixture.searchHistoryTool).execute(any());
        verify(fixture.queryTicketTool, never()).execute(any());
        verify(fixture.createTicketTool, never()).execute(any());
        verify(fixture.transferTicketTool, never()).execute(any());
    }

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
        assertNotNull(result.getPlan());
        assertEquals("trace-1", result.getTraceId());
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
    void chatShouldCreatePendingActionWhenCreateTicketMissingFields() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().build();
        when(fixture.sessionService.load("s-create-missing")).thenReturn(context);
        when(fixture.intentRouter.route("创建工单", context)).thenReturn(IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.91d)
                .reason("命中创建关键词")
                .build());
        when(fixture.parameterExtractor.extract("创建工单", context)).thenReturn(AgentToolParameters.builder().build());
        when(fixture.executionGuard.check(any(), eq("创建工单"), eq(context), any(), any()))
                .thenReturn(AgentExecutionDecision.needMoreInfo(
                        fixture.createTicketTool,
                        List.of(AgentToolParameterField.TITLE, AgentToolParameterField.DESCRIPTION)
                ));

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-create-missing", "创建工单");

        assertEquals("CREATE_TICKET", result.getIntent());
        assertEquals("我先记录了当前工单草稿。还需要补充：工单标题、问题描述。消息内容：创建工单", result.getReply());
        assertNotNull(result.getContext().getPendingAction());
        assertEquals(AgentIntent.CREATE_TICKET, result.getContext().getPendingAction().getPendingIntent());
        assertEquals(List.of(AgentToolParameterField.TITLE, AgentToolParameterField.DESCRIPTION),
                result.getContext().getPendingAction().getAwaitingFields());
        verify(fixture.createTicketTool, never()).execute(any());
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
    void chatShouldCancelPendingCreateDraft() {
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
        when(fixture.sessionService.load("s-create-cancel")).thenReturn(context);

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-create-cancel", "取消");

        assertEquals("CREATE_TICKET", result.getIntent());
        assertEquals("已取消本次工单创建。你可以随时重新发起新的创建请求。", result.getReply());
        assertNull(result.getContext().getPendingAction());
        verify(fixture.createTicketTool, never()).execute(any());
    }

    @Test
    void chatShouldBypassReactForWriteIntentEvenWhenSpringAiReady() {
        TestFixture fixture = fixture(true);
        AgentSessionContext context = AgentSessionContext.builder().build();
        when(fixture.sessionService.load("s-create-no-react")).thenReturn(context);
        when(fixture.intentRouter.route("创建线上登录失败工单", context)).thenReturn(IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.95d)
                .reason("创建工单")
                .build());
        when(fixture.parameterExtractor.extract("创建线上登录失败工单", context)).thenReturn(AgentToolParameters.builder()
                .title("线上登录失败")
                .description("线上登录失败，影响全部用户")
                .build());
        when(fixture.executionGuard.check(any(), eq("创建线上登录失败工单"), eq(context), any(), any()))
                .thenReturn(AgentExecutionDecision.allow(fixture.createTicketTool));
        when(fixture.createTicketTool.execute(any())).thenReturn(AgentToolResult.builder()
                .status(AgentToolStatus.SUCCESS)
                .toolName("createTicket")
                .reply("已创建工单。")
                .build());

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-create-no-react", "创建线上登录失败工单");

        assertEquals("CREATE_TICKET", result.getIntent());
        assertEquals("已创建工单。", result.getReply());
        verifyNoInteractions(fixture.chatClient);
        verify(fixture.createTicketTool).execute(any());
    }

    @Test
    void chatShouldUseReadOnlyReactExecutorWhenPolicyAllowsReact() {
        TestFixture fixture = fixture(true);
        AgentSessionContext context = AgentSessionContext.builder().activeTicketId(1001L).build();
        ChatResponse response = mock(ChatResponse.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(fixture.sessionService.load("s-query-react")).thenReturn(context);
        when(fixture.intentRouter.route("查询工单1001", context)).thenReturn(IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.96d)
                .reason("命中查询关键词")
                .build());
        when(fixture.skillRegistry.findAvailable(eq(AgentIntent.QUERY_TICKET), any(), eq(ToolRiskLevel.READ_ONLY)))
                .thenReturn(List.of(fixture.querySkill));
        when(response.getResult().getOutput().getText()).thenReturn("这是只读 ReAct 的总结结果。");
        when(fixture.chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .tools(any(Object[].class))
                .toolContext(anyMap())
                .stream()
                .chatResponse())
                .thenReturn(Flux.just(response));

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-query-react", "查询工单1001");

        assertEquals("QUERY_TICKET", result.getIntent());
        assertEquals("这是只读 ReAct 的总结结果。", result.getReply());
        verify(fixture.queryTicketTool, never()).execute(any());
        verify(fixture.sessionService, never()).resolveReferences(any(), any(), any());
        verify(fixture.traceService).recordReasoning(any(), eq("这是只读 ReAct 的总结结果。"));
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
        verify(fixture.agentPlanner).markNeedConfirmation(any(), eq("risk"));
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

    @Test
    void chatShouldCancelPendingHighRiskTransfer() {
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
        when(fixture.sessionService.load("s-cancel-confirm")).thenReturn(context);

        AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-cancel-confirm", "取消");

        assertEquals("TRANSFER_TICKET", result.getIntent());
        assertEquals("已取消本次高风险操作，未执行任何变更。", result.getReply());
        assertNull(result.getContext().getPendingAction());
        verify(fixture.transferTicketTool, never()).execute(any());
    }

    @Test
    void chatShouldRejectWhenSameSessionIsBusy() throws InterruptedException {
        TestFixture fixture = fixture();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            fixture.sessionLockService.tryLock("s-busy");
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                fixture.sessionLockService.unlock("s-busy");
            }
        });
        holder.start();
        try {
            locked.await();
            AgentChatResult result = fixture.agentFacade.chat(currentUser(), "s-busy", "查询工单1001");

            assertEquals("当前会话已有一条消息正在处理中，请稍后再试。", result.getReply());
            assertEquals("trace-1", result.getTraceId());
            verify(fixture.sessionService, never()).load(anyString());
        } finally {
            release.countDown();
            holder.join();
        }
    }

    @Test
    void chatStreamShouldEmitAcceptedRouteStatusAndFinalResult() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().build();
        AgentToolResult toolResult = AgentToolResult.builder()
                .status(AgentToolStatus.SUCCESS)
                .toolName("queryTicket")
                .reply("已查询工单详情。")
                .data("ticket-detail")
                .activeTicketId(1001L)
                .build();
        when(fixture.sessionService.load("s-stream")).thenReturn(context);
        when(fixture.intentRouter.route("查询工单1001", context)).thenReturn(IntentRoute.builder()
                .intent(AgentIntent.QUERY_TICKET)
                .confidence(0.92d)
                .reason("命中查询关键词")
                .build());
        when(fixture.parameterExtractor.extract("查询工单1001", context)).thenReturn(AgentToolParameters.builder()
                .ticketId(1001L)
                .build());
        when(fixture.executionGuard.check(any(), eq("查询工单1001"), eq(context), any(), any()))
                .thenReturn(AgentExecutionDecision.allow(fixture.queryTicketTool));
        when(fixture.queryTicketTool.execute(any())).thenReturn(toolResult);
        CapturingSink sink = new CapturingSink();

        AgentChatResult result = fixture.agentFacade.chatStream(currentUser(), "s-stream", "查询工单1001", sink);

        assertEquals("QUERY_TICKET", result.getIntent());
        assertEquals(result, sink.finalResult);
        assertEquals(true, sink.events.contains("accepted:s-stream"));
        assertEquals(true, sink.events.stream().anyMatch(event -> event.startsWith("route:QUERY_TICKET")));
        assertEquals(true, sink.events.stream().anyMatch(event -> event.startsWith("status:")));
        assertEquals(true, sink.closed);
    }

    private TestFixture fixture() {
        return fixture(false);
    }

    private TestFixture fixture(boolean chatEnabled) {
        ObjectProvider<?> chatClientProvider = mock(ObjectProvider.class);
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        IntentRouter intentRouter = mock(IntentRouter.class);
        AgentSessionService sessionService = mock(AgentSessionService.class);
        AgentExecutionGuard executionGuard = mock(AgentExecutionGuard.class);
        AgentPlanner agentPlanner = mock(AgentPlanner.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentMemoryService memoryService = mock(AgentMemoryService.class);
        AgentTraceService traceService = mock(AgentTraceService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        AgentToolParameterExtractor parameterExtractor = mock(AgentToolParameterExtractor.class);
        AgentReplyRenderer agentReplyRenderer = new AgentReplyRenderer();
        QueryTicketTool queryTicketTool = mock(QueryTicketTool.class);
        CreateTicketTool createTicketTool = mock(CreateTicketTool.class);
        TransferTicketTool transferTicketTool = mock(TransferTicketTool.class);
        SearchHistoryTool searchHistoryTool = mock(SearchHistoryTool.class);
        CreateTicketCommandHandler createHandler = new CreateTicketCommandHandler(createTicketTool);
        TransferTicketCommandHandler transferHandler = new TransferTicketCommandHandler(transferTicketTool);
        PendingActionCoordinator pendingActionCoordinator = new PendingActionCoordinator(
                agentPlanner,
                parameterExtractor,
                List.of(createHandler, transferHandler)
        );
        AgentExecutionPolicyService executionPolicyService = new AgentExecutionPolicyService(skillRegistry);
        AgentReactToolCatalog reactToolCatalog = new AgentReactToolCatalog();
        ReadOnlyReactExecutor readOnlyReactExecutor = new ReadOnlyReactExecutor(
                (ObjectProvider) chatClientProvider,
                agentPlanner,
                promptTemplateService,
                traceService,
                reactToolCatalog,
                new AgentTurnBudgetService()
        );
        AgentTurnBudgetService budgetService = new AgentTurnBudgetService();
        AgentSessionLockService sessionLockService = new AgentSessionLockService();
        DeterministicCommandExecutor deterministicCommandExecutor = new DeterministicCommandExecutor(
                parameterExtractor,
                sessionService,
                executionGuard,
                pendingActionCoordinator,
                agentPlanner,
                List.of(createHandler, transferHandler)
        );
        when(createTicketTool.name()).thenReturn("createTicket");
        when(transferTicketTool.name()).thenReturn("transferTicket");
        when(queryTicketTool.name()).thenReturn("queryTicket");
        when(searchHistoryTool.name()).thenReturn("searchHistory");
        when(createTicketTool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name("createTicket")
                .riskLevel(ToolRiskLevel.LOW_RISK_WRITE)
                .readOnly(false)
                .build());
        when(transferTicketTool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name("transferTicket")
                .riskLevel(ToolRiskLevel.HIGH_RISK_WRITE)
                .readOnly(false)
                .requireConfirmation(true)
                .build());
        when(queryTicketTool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name("queryTicket")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .readOnly(true)
                .build());
        when(searchHistoryTool.metadata()).thenReturn(AgentToolMetadata.builder()
                .name("searchHistory")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .readOnly(true)
                .build());
        AgentSkill createSkill = mock(AgentSkill.class);
        AgentSkill querySkill = mock(AgentSkill.class);
        AgentSkill transferSkill = mock(AgentSkill.class);
        AgentSkill searchSkill = mock(AgentSkill.class);
        when(createSkill.tool()).thenReturn(createTicketTool);
        when(querySkill.tool()).thenReturn(queryTicketTool);
        when(transferSkill.tool()).thenReturn(transferTicketTool);
        when(searchSkill.tool()).thenReturn(searchHistoryTool);
        stubSkill(createSkill, "create-ticket", AgentIntent.CREATE_TICKET, ToolRiskLevel.LOW_RISK_WRITE, true);
        stubSkill(querySkill, "query-ticket", AgentIntent.QUERY_TICKET, ToolRiskLevel.READ_ONLY, true);
        stubSkill(transferSkill, "transfer-ticket", AgentIntent.TRANSFER_TICKET, ToolRiskLevel.HIGH_RISK_WRITE, false);
        stubSkill(searchSkill, "search-history", AgentIntent.SEARCH_HISTORY, ToolRiskLevel.READ_ONLY, true);
        when(skillRegistry.requireByIntent(AgentIntent.CREATE_TICKET)).thenReturn(createSkill);
        when(skillRegistry.requireByIntent(AgentIntent.QUERY_TICKET)).thenReturn(querySkill);
        when(skillRegistry.requireByIntent(AgentIntent.TRANSFER_TICKET)).thenReturn(transferSkill);
        when(skillRegistry.requireByIntent(AgentIntent.SEARCH_HISTORY)).thenReturn(searchSkill);
        when(skillRegistry.requireByToolName("createTicket")).thenReturn(createSkill);
        when(skillRegistry.requireByToolName("queryTicket")).thenReturn(querySkill);
        when(skillRegistry.requireByToolName("transferTicket")).thenReturn(transferSkill);
        when(skillRegistry.requireByToolName("searchHistory")).thenReturn(searchSkill);
        when(skillRegistry.findAvailable(eq(AgentIntent.CREATE_TICKET), any(), eq(ToolRiskLevel.LOW_RISK_WRITE)))
                .thenReturn(List.of(createSkill));
        when(skillRegistry.findAvailable(eq(AgentIntent.QUERY_TICKET), any(), eq(ToolRiskLevel.READ_ONLY)))
                .thenReturn(List.of(querySkill));
        when(skillRegistry.findAvailable(eq(AgentIntent.TRANSFER_TICKET), any(), eq(ToolRiskLevel.HIGH_RISK_WRITE)))
                .thenReturn(List.of(transferSkill));
        when(skillRegistry.findAvailable(eq(AgentIntent.SEARCH_HISTORY), any(), eq(ToolRiskLevel.READ_ONLY)))
                .thenReturn(List.of(searchSkill));
        when(promptTemplateService.content(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(traceService.start(any(), any(), any())).thenReturn(new AgentTraceContext("trace-1", "session", 1L, "message"));
        when(((ObjectProvider<ChatClient>) chatClientProvider).getIfAvailable()).thenReturn(chatEnabled ? chatClient : null);
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
                chatEnabled,
                intentRouter,
                sessionService,
                executionGuard,
                agentPlanner,
                skillRegistry,
                memoryService,
                traceService,
                parameterExtractor,
                agentReplyRenderer,
                pendingActionCoordinator,
                deterministicCommandExecutor,
                executionPolicyService,
                readOnlyReactExecutor,
                new AgentRateLimitService(100, 1000),
                sessionLockService,
                budgetService,
                new AgentDegradePolicyService()
        );
        return new TestFixture(
                agentFacade,
                chatClient,
                intentRouter,
                sessionService,
                executionGuard,
                agentPlanner,
                skillRegistry,
                memoryService,
                traceService,
                promptTemplateService,
                parameterExtractor,
                sessionLockService,
                querySkill,
                queryTicketTool,
                createTicketTool,
                transferTicketTool,
                searchHistoryTool
        );
    }

    private record TestFixture(
            AgentFacade agentFacade,
            ChatClient chatClient,
            IntentRouter intentRouter,
            AgentSessionService sessionService,
            AgentExecutionGuard executionGuard,
            AgentPlanner agentPlanner,
            SkillRegistry skillRegistry,
            AgentMemoryService memoryService,
            AgentTraceService traceService,
            PromptTemplateService promptTemplateService,
            AgentToolParameterExtractor parameterExtractor,
            AgentSessionLockService sessionLockService,
            AgentSkill querySkill,
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
                .roles(List.of("USER", "STAFF"))
                .build();
    }

    private void stubSkill(
            AgentSkill skill,
            String skillCode,
            AgentIntent intent,
            ToolRiskLevel riskLevel,
            boolean canAutoExecute
    ) {
        when(skill.skillCode()).thenReturn(skillCode);
        when(skill.supportedIntents()).thenReturn(List.of(intent));
        when(skill.requiredPermissions()).thenReturn(List.of());
        when(skill.riskLevel()).thenReturn(riskLevel);
        when(skill.canAutoExecute()).thenReturn(canAutoExecute);
    }

    private static class CapturingSink implements AgentEventSink {
        private final List<String> events = new ArrayList<>();
        private AgentChatResult finalResult;
        private boolean closed;

        @Override
        public void accepted(String sessionId) {
            events.add("accepted:" + sessionId);
        }

        @Override
        public void status(String message) {
            events.add("status:" + message);
        }

        @Override
        public void route(IntentRoute route) {
            events.add("route:" + (route == null || route.getIntent() == null ? null : route.getIntent().name()));
        }

        @Override
        public void delta(String text) {
            events.add("delta:" + text);
        }

        @Override
        public void finalResult(AgentChatResult result) {
            this.finalResult = result;
            events.add("final");
        }

        @Override
        public void error(String errorCode, String message, String traceId) {
            events.add("error:" + errorCode);
        }

        @Override
        public void closeQuietly() {
            closed = true;
        }
    }
}
