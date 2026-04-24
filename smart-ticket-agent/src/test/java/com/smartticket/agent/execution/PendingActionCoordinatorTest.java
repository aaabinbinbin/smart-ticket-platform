package com.smartticket.agent.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.agent.command.CreateTicketCommandHandler;
import com.smartticket.agent.command.TransferTicketCommandHandler;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanAction;
import com.smartticket.agent.planner.AgentPlanStage;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.ticket.CreateTicketTool;
import com.smartticket.agent.tool.ticket.TransferTicketTool;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PendingActionCoordinator 的 P2 回归测试。
 *
 * <p>这些测试只保护 pendingAction 的唯一入口职责：创建确认态、创建草稿、补参续办、
 * 高风险确认执行和取消清理，不覆盖 facade 的 trace/session 提交细节。</p>
 */
class PendingActionCoordinatorTest {

    @Test
    void prepareConfirmationShouldCreateTransferPendingAction() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().build();
        IntentRoute route = IntentRoute.builder()
                .intent(AgentIntent.TRANSFER_TICKET)
                .confidence(0.90d)
                .reason("高风险转派")
                .build();
        AgentToolParameters parameters = AgentToolParameters.builder()
                .ticketId(1001L)
                .assigneeId(3L)
                .build();

        AgentToolResult result = fixture.coordinator.prepareConfirmation(
                context,
                route,
                parameters,
                "transferTicket",
                "高风险转派"
        );

        assertEquals(AgentToolStatus.NEED_MORE_INFO, result.getStatus());
        assertEquals("transferTicket", result.getToolName());
        assertNotNull(context.getPendingAction());
        assertEquals(AgentIntent.TRANSFER_TICKET, context.getPendingAction().getPendingIntent());
        assertEquals(true, context.getPendingAction().isAwaitingConfirmation());
        assertEquals(1001L, context.getPendingAction().getPendingParameters().getTicketId());
        assertEquals(3L, context.getPendingAction().getPendingParameters().getAssigneeId());
    }

    @Test
    void syncPendingActionShouldCreateDraftForCreateTicket() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().build();
        IntentRoute route = IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.95d)
                .reason("创建工单")
                .build();
        AgentToolParameters parameters = AgentToolParameters.builder()
                .title("测试环境登录失败")
                .build();
        AgentToolResult toolResult = AgentToolResult.builder()
                .status(AgentToolStatus.NEED_MORE_INFO)
                .toolName("createTicket")
                .data(List.of(AgentToolParameterField.DESCRIPTION, AgentToolParameterField.PRIORITY))
                .build();

        fixture.coordinator.syncPendingAction(context, route, parameters, toolResult, "创建工单");

        assertNotNull(context.getPendingAction());
        assertEquals(AgentIntent.CREATE_TICKET, context.getPendingAction().getPendingIntent());
        assertEquals("createTicket", context.getPendingAction().getPendingToolName());
        assertEquals("测试环境登录失败", context.getPendingAction().getPendingParameters().getTitle());
        assertEquals(List.of(AgentToolParameterField.DESCRIPTION, AgentToolParameterField.PRIORITY),
                context.getPendingAction().getAwaitingFields());
        assertEquals("我先记录了当前工单草稿（标题：测试环境登录失败）。还需要补充：问题描述、工单优先级。消息内容：创建工单", toolResult.getReply());
    }

    @Test
    void continuePendingActionShouldMergeCreateDraftAndKeepPending() {
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
        when(fixture.parameterExtractor.extract("系统类", context)).thenReturn(AgentToolParameters.builder()
                .category(TicketCategoryEnum.SYSTEM)
                .build());
        when(fixture.createTicketTool.execute(argThat(request ->
                request.getParameters().getCategory() == TicketCategoryEnum.SYSTEM
                        && request.getParameters().getPriority() == null
                        && "测试环境登录失败".equals(request.getParameters().getTitle())
        ))).thenReturn(AgentToolResult.builder()
                .status(AgentToolStatus.NEED_MORE_INFO)
                .toolName("createTicket")
                .data(List.of(AgentToolParameterField.PRIORITY))
                .build());

        PendingActionCoordinator.PendingContinuation continuation = fixture.coordinator.continuePendingAction(
                currentUser(),
                "系统类",
                context
        );

        AgentExecutionSummary summary = continuation.getSummary();
        assertEquals(AgentIntent.CREATE_TICKET, continuation.getRoute().getIntent());
        assertEquals(AgentTurnStatus.NEED_MORE_INFO, summary.getStatus());
        assertNotNull(context.getPendingAction());
        assertEquals(TicketCategoryEnum.SYSTEM, context.getPendingAction().getPendingParameters().getCategory());
        assertEquals(List.of(AgentToolParameterField.PRIORITY), context.getPendingAction().getAwaitingFields());
        assertEquals("请补充工单优先级：LOW、MEDIUM、HIGH、URGENT。", summary.getPrimaryResult().getReply());
        verify(fixture.agentPlanner).afterTool(any(), any());
    }

    @Test
    void continuePendingActionShouldCancelPendingCreate() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder()
                .pendingAction(AgentPendingAction.builder()
                        .pendingIntent(AgentIntent.CREATE_TICKET)
                        .pendingToolName("createTicket")
                        .pendingParameters(AgentToolParameters.builder()
                                .title("测试环境登录失败")
                                .description("测试环境登录报错 500")
                                .build())
                        .awaitingFields(List.of(AgentToolParameterField.PRIORITY))
                        .build())
                .build();

        PendingActionCoordinator.PendingContinuation continuation = fixture.coordinator.continuePendingAction(
                currentUser(),
                "取消",
                context
        );

        assertEquals(AgentTurnStatus.CANCELLED, continuation.getSummary().getStatus());
        assertEquals("已取消本次工单创建。你可以随时重新发起新的创建请求。", continuation.getSummary().getPrimaryResult().getReply());
        assertNull(context.getPendingAction());
        verify(fixture.createTicketTool, never()).execute(any());
    }

    @Test
    void continuePendingActionShouldExecuteTransferAfterConfirmation() {
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
                        .confirmationSummary("请确认")
                        .build())
                .build();
        when(fixture.transferTicketTool.execute(argThat(request ->
                request.getParameters().getTicketId().equals(1001L)
                        && request.getParameters().getAssigneeId().equals(3L)
        ))).thenReturn(AgentToolResult.builder()
                .invoked(true)
                .status(AgentToolStatus.SUCCESS)
                .toolName("transferTicket")
                .reply("已转派工单。")
                .activeTicketId(1001L)
                .activeAssigneeId(3L)
                .build());

        PendingActionCoordinator.PendingContinuation continuation = fixture.coordinator.continuePendingAction(
                currentUser(),
                "确认执行",
                context
        );

        assertEquals(AgentTurnStatus.COMPLETED, continuation.getSummary().getStatus());
        assertEquals("已转派工单。", continuation.getSummary().getPrimaryResult().getReply());
        assertNull(context.getPendingAction());
        verify(fixture.transferTicketTool).execute(any());
    }

    private TestFixture fixture() {
        AgentPlanner agentPlanner = mock(AgentPlanner.class);
        AgentToolParameterExtractor parameterExtractor = mock(AgentToolParameterExtractor.class);
        CreateTicketTool createTicketTool = mock(CreateTicketTool.class);
        TransferTicketTool transferTicketTool = mock(TransferTicketTool.class);
        when(createTicketTool.name()).thenReturn("createTicket");
        when(transferTicketTool.name()).thenReturn("transferTicket");
        when(agentPlanner.buildOrLoadPlan(any(), any())).thenReturn(AgentPlan.builder()
                .goal("pending")
                .intent(AgentIntent.CREATE_TICKET)
                .currentStage(AgentPlanStage.WAIT_USER)
                .nextAction(AgentPlanAction.COLLECT_SLOTS)
                .nextSkillCode("create-ticket")
                .riskLevel(ToolRiskLevel.LOW_RISK_WRITE)
                .build());
        CreateTicketCommandHandler createHandler = new CreateTicketCommandHandler(createTicketTool);
        TransferTicketCommandHandler transferHandler = new TransferTicketCommandHandler(transferTicketTool);
        PendingActionCoordinator coordinator = new PendingActionCoordinator(
                agentPlanner,
                parameterExtractor,
                List.of(createHandler, transferHandler)
        );
        return new TestFixture(coordinator, agentPlanner, parameterExtractor, createTicketTool, transferTicketTool);
    }

    private record TestFixture(
            PendingActionCoordinator coordinator,
            AgentPlanner agentPlanner,
            AgentToolParameterExtractor parameterExtractor,
            CreateTicketTool createTicketTool,
            TransferTicketTool transferTicketTool
    ) {
    }

    private static CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
