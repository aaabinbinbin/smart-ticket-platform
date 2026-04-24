package com.smartticket.agent.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.agent.command.AgentCommandDraft;
import com.smartticket.agent.command.CreateTicketCommandHandler;
import com.smartticket.agent.command.TransferTicketCommandHandler;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanAction;
import com.smartticket.agent.planner.AgentPlanStage;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.service.AgentSessionService;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.ticket.CreateTicketTool;
import com.smartticket.agent.tool.ticket.TransferTicketTool;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.domain.enums.TicketPriorityEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DeterministicCommandExecutor 的 P3 单元测试。
 *
 * <p>这些测试只保护写命令主链的关键顺序：先参数提取和指代补齐，再进入 Guard，
 * 然后根据结果进入补参、确认或最终执行，不覆盖 facade 的 trace/session 提交细节。</p>
 */
class DeterministicCommandExecutorTest {

    @Test
    void executeShouldCreateDraftWhenCreateTicketStillMissingFields() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().build();
        AgentPlan plan = plan();
        IntentRoute route = route(AgentIntent.CREATE_TICKET);
        AgentToolParameters parameters = AgentToolParameters.builder().build();
        when(fixture.parameterExtractor.extract("创建工单", context)).thenReturn(parameters);
        when(fixture.executionGuard.check(any(), eq("创建工单"), eq(context), eq(route), any()))
                .thenReturn(AgentExecutionDecision.needMoreInfo(
                        fixture.createTicketTool,
                        List.of(AgentToolParameterField.TITLE, AgentToolParameterField.DESCRIPTION)
                ));

        AgentExecutionSummary summary = fixture.executor.execute(currentUser(), "创建工单", context, route, plan);

        assertEquals(AgentTurnStatus.NEED_MORE_INFO, summary.getStatus());
        assertEquals(AgentExecutionMode.WRITE_COMMAND_DRAFT, summary.getMode());
        assertNotNull(summary.getPendingAction());
        assertEquals(List.of(AgentToolParameterField.TITLE, AgentToolParameterField.DESCRIPTION),
                summary.getPendingAction().getAwaitingFields());
        assertNotNull(summary.getCommandDraft());
        assertEquals("createTicket", summary.getCommandDraft().getToolName());
        verify(fixture.createTicketTool, never()).execute(any());
        verify(fixture.sessionService).resolveReferences("创建工单", context, parameters);
    }

    @Test
    void executeShouldRequireConfirmationBeforeTransfer() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().build();
        AgentPlan plan = plan();
        IntentRoute route = route(AgentIntent.TRANSFER_TICKET);
        AgentToolParameters parameters = AgentToolParameters.builder()
                .ticketId(1001L)
                .assigneeId(3L)
                .build();
        when(fixture.parameterExtractor.extract("转派给3", context)).thenReturn(parameters);
        when(fixture.executionGuard.check(any(), eq("转派给3"), eq(context), eq(route), any()))
                .thenReturn(AgentExecutionDecision.needConfirmation(fixture.transferTicketTool, "risk"));

        AgentExecutionSummary summary = fixture.executor.execute(currentUser(), "转派给3", context, route, plan);

        assertEquals(AgentTurnStatus.NEED_CONFIRMATION, summary.getStatus());
        assertEquals(AgentExecutionMode.HIGH_RISK_CONFIRMATION, summary.getMode());
        assertNotNull(summary.getPendingAction());
        assertEquals(true, summary.getCommandDraft().isConfirmationRequired());
        verify(fixture.agentPlanner).markNeedConfirmation(plan, "risk");
        verify(fixture.transferTicketTool, never()).execute(any());
    }

    @Test
    void executeShouldRunCreateCommandAfterGuardAllows() {
        TestFixture fixture = fixture();
        AgentSessionContext context = AgentSessionContext.builder().build();
        AgentPlan plan = plan();
        IntentRoute route = route(AgentIntent.CREATE_TICKET);
        AgentToolParameters parameters = AgentToolParameters.builder()
                .title("线上登录失败")
                .description("线上登录失败，影响全部用户")
                .priority(TicketPriorityEnum.HIGH)
                .build();
        when(fixture.parameterExtractor.extract("创建线上登录失败工单", context)).thenReturn(parameters);
        when(fixture.executionGuard.check(any(), eq("创建线上登录失败工单"), eq(context), eq(route), any()))
                .thenReturn(AgentExecutionDecision.allow(fixture.createTicketTool));
        when(fixture.createTicketTool.execute(any())).thenReturn(AgentToolResult.builder()
                .invoked(true)
                .status(AgentToolStatus.SUCCESS)
                .toolName("createTicket")
                .reply("已创建工单。")
                .build());

        AgentExecutionSummary summary = fixture.executor.execute(currentUser(), "创建线上登录失败工单", context, route, plan);

        assertEquals(AgentTurnStatus.COMPLETED, summary.getStatus());
        assertEquals(AgentExecutionMode.WRITE_COMMAND_EXECUTE, summary.getMode());
        AgentCommandDraft commandDraft = summary.getCommandDraft();
        assertNotNull(commandDraft);
        assertEquals("createTicket", commandDraft.getToolName());
        assertEquals("线上登录失败", commandDraft.getParameters().getTitle());
        verify(fixture.createTicketTool).execute(any());
        verify(fixture.agentPlanner).afterTool(plan, summary.getPrimaryResult());
    }

    private TestFixture fixture() {
        AgentToolParameterExtractor parameterExtractor = mock(AgentToolParameterExtractor.class);
        AgentSessionService sessionService = mock(AgentSessionService.class);
        AgentExecutionGuard executionGuard = mock(AgentExecutionGuard.class);
        AgentPlanner agentPlanner = mock(AgentPlanner.class);
        CreateTicketTool createTicketTool = mock(CreateTicketTool.class);
        TransferTicketTool transferTicketTool = mock(TransferTicketTool.class);
        when(createTicketTool.name()).thenReturn("createTicket");
        when(transferTicketTool.name()).thenReturn("transferTicket");
        PendingActionCoordinator pendingActionCoordinator = new PendingActionCoordinator(
                agentPlanner,
                parameterExtractor,
                List.of(
                        new CreateTicketCommandHandler(createTicketTool),
                        new TransferTicketCommandHandler(transferTicketTool)
                )
        );
        DeterministicCommandExecutor executor = new DeterministicCommandExecutor(
                parameterExtractor,
                sessionService,
                executionGuard,
                pendingActionCoordinator,
                agentPlanner,
                List.of(
                        new CreateTicketCommandHandler(createTicketTool),
                        new TransferTicketCommandHandler(transferTicketTool)
                )
        );
        return new TestFixture(executor, parameterExtractor, sessionService, executionGuard, agentPlanner, createTicketTool, transferTicketTool);
    }

    private record TestFixture(
            DeterministicCommandExecutor executor,
            AgentToolParameterExtractor parameterExtractor,
            AgentSessionService sessionService,
            AgentExecutionGuard executionGuard,
            AgentPlanner agentPlanner,
            CreateTicketTool createTicketTool,
            TransferTicketTool transferTicketTool
    ) {
    }

    private static AgentPlan plan() {
        return AgentPlan.builder()
                .goal("write-command")
                .intent(AgentIntent.CREATE_TICKET)
                .currentStage(AgentPlanStage.EXECUTE_SKILL)
                .nextAction(AgentPlanAction.EXECUTE_TOOL)
                .nextSkillCode("command")
                .riskLevel(ToolRiskLevel.LOW_RISK_WRITE)
                .build();
    }

    private static IntentRoute route(AgentIntent intent) {
        return IntentRoute.builder()
                .intent(intent)
                .confidence(0.95d)
                .reason("test")
                .build();
    }

    private static CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
