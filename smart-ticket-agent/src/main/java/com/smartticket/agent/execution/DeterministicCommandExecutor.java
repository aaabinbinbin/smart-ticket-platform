package com.smartticket.agent.execution;

import com.smartticket.agent.command.AgentCommandDraft;
import com.smartticket.agent.command.AgentCommandHandler;
import com.smartticket.agent.command.AgentCommandType;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.service.AgentSessionService;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 确定性写命令执行器。
 *
 * <p>该类位于 Agent 主链的写操作执行层，负责把 CREATE_TICKET、TRANSFER_TICKET 这类写请求
 * 固定收敛为“参数提取 -> 参数校验 -> 风险/权限校验 -> 必要确认 -> 确定性执行 -> summary”。
 * 它允许调用确定性写工具，但不会保存 session、不会写 memory、不会记录 trace，
 * 这些提交动作仍由外层编排统一完成。</p>
 */
@Service
public class DeterministicCommandExecutor {

    private final AgentToolParameterExtractor parameterExtractor;
    private final AgentSessionService sessionService;
    private final AgentExecutionGuard executionGuard;
    private final PendingActionCoordinator pendingActionCoordinator;
    private final AgentPlanner agentPlanner;
    private final Map<AgentIntent, AgentCommandHandler> handlersByIntent;

    public DeterministicCommandExecutor(
            AgentToolParameterExtractor parameterExtractor,
            AgentSessionService sessionService,
            AgentExecutionGuard executionGuard,
            PendingActionCoordinator pendingActionCoordinator,
            AgentPlanner agentPlanner,
            List<AgentCommandHandler> commandHandlers
    ) {
        this.parameterExtractor = parameterExtractor;
        this.sessionService = sessionService;
        this.executionGuard = executionGuard;
        this.pendingActionCoordinator = pendingActionCoordinator;
        this.agentPlanner = agentPlanner;
        this.handlersByIntent = buildHandlerIndex(commandHandlers);
    }

    /**
     * 执行写操作命令主链。
     *
     * <p>该方法只面向写意图使用。它会生成命令草稿并根据 Guard 结果决定是继续补参、进入确认，
     * 还是真正执行写命令，但不会直接保存 session、memory、pendingAction 或 trace。</p>
     *
     * @param currentUser 当前登录用户
     * @param message 用户原始消息
     * @param context 当前会话上下文
     * @param route 当前路由结果，必须是写意图
     * @param plan 当前轮计划状态
     * @return 当前写命令链路的统一结果摘要
     */
    public AgentExecutionSummary execute(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan
    ) {
        AgentCommandHandler handler = requireHandler(route.getIntent());
        agentPlanner.beforeExecute(plan);

        AgentToolParameters parameters = parameterExtractor.extract(message, context);
        sessionService.resolveReferences(message, context, parameters);
        AgentCommandDraft draft = buildDraft(route, handler, parameters);

        AgentExecutionDecision decision = executionGuard.check(
                currentUser,
                message,
                context,
                route,
                buildToolCallPlan(route, handler, parameters)
        );
        enrichDraft(draft, decision);

        AgentToolResult toolResult;
        if (decision.isAllowed()) {
            toolResult = handler.execute(currentUser, message, context, route, parameters);
            pendingActionCoordinator.syncPendingAction(context, route, parameters, toolResult, message);
            agentPlanner.afterTool(plan, toolResult);
        } else if (decision.getStatus() == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            // 高风险写操作必须先停在确认态，避免直接进入确定性执行。
            toolResult = pendingActionCoordinator.prepareConfirmation(context, route, parameters, handler.toolName(), decision.getReason());
            agentPlanner.markNeedConfirmation(plan, decision.getReason());
        } else {
            toolResult = decision.toToolResult(handler.toolName());
            pendingActionCoordinator.syncPendingAction(context, route, parameters, toolResult, message);
            agentPlanner.afterTool(plan, toolResult);
        }
        return buildSummary(route, parameters, draft, toolResult, context, decision);
    }

    private Map<AgentIntent, AgentCommandHandler> buildHandlerIndex(List<AgentCommandHandler> commandHandlers) {
        Map<AgentIntent, AgentCommandHandler> handlers = new LinkedHashMap<>();
        if (commandHandlers == null) {
            return handlers;
        }
        for (AgentCommandHandler handler : commandHandlers) {
            handlers.put(handler.supportIntent(), handler);
        }
        return handlers;
    }

    private AgentCommandHandler requireHandler(AgentIntent intent) {
        AgentCommandHandler handler = handlersByIntent.get(intent);
        if (handler == null) {
            throw new IllegalArgumentException("未找到写命令处理器: " + intent);
        }
        return handler;
    }

    private ToolCallPlan buildToolCallPlan(
            IntentRoute route,
            AgentCommandHandler handler,
            AgentToolParameters parameters
    ) {
        return ToolCallPlan.builder()
                .intent(route.getIntent())
                .toolName(handler.toolName())
                .parameters(parameters)
                .llmGenerated(false)
                .reason("确定性写命令执行")
                .build();
    }

    private AgentCommandDraft buildDraft(
            IntentRoute route,
            AgentCommandHandler handler,
            AgentToolParameters parameters
    ) {
        return AgentCommandDraft.builder()
                .commandType(AgentCommandType.fromIntent(route.getIntent()))
                .intent(route.getIntent())
                .toolName(handler.toolName())
                .parameters(parameters)
                .previewText(previewText(route.getIntent(), parameters))
                .build();
    }

    private void enrichDraft(AgentCommandDraft draft, AgentExecutionDecision decision) {
        if (draft == null || decision == null) {
            return;
        }
        if (decision.getMissingFields() != null) {
            draft.setMissingFields(decision.getMissingFields());
        }
        if (decision.getStatus() == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            draft.setConfirmationRequired(true);
        }
    }

    private AgentExecutionSummary buildSummary(
            IntentRoute route,
            AgentToolParameters parameters,
            AgentCommandDraft draft,
            AgentToolResult toolResult,
            AgentSessionContext context,
            AgentExecutionDecision decision
    ) {
        return AgentExecutionSummary.builder()
                .status(summarizeStatus(decision, toolResult, context))
                .mode(summarizeMode(decision, toolResult))
                .intent(route.getIntent())
                .parameters(parameters)
                .commandDraft(draft)
                .primaryResult(toolResult)
                .pendingAction(context == null ? null : context.getPendingAction())
                .springAiUsed(false)
                .fallbackUsed(false)
                .toolInvoked(toolResult != null && toolResult.isInvoked())
                .build();
    }

    private AgentExecutionMode summarizeMode(
            AgentExecutionDecision decision,
            AgentToolResult toolResult
    ) {
        if (decision != null && decision.getStatus() == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            return AgentExecutionMode.HIGH_RISK_CONFIRMATION;
        }
        if (toolResult != null && toolResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            return AgentExecutionMode.WRITE_COMMAND_DRAFT;
        }
        return AgentExecutionMode.WRITE_COMMAND_EXECUTE;
    }

    private AgentTurnStatus summarizeStatus(
            AgentExecutionDecision decision,
            AgentToolResult toolResult,
            AgentSessionContext context
    ) {
        if (decision != null && decision.getStatus() == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            return AgentTurnStatus.NEED_CONFIRMATION;
        }
        if (toolResult == null) {
            return AgentTurnStatus.FAILED;
        }
        if (toolResult.getStatus() == AgentToolStatus.SUCCESS) {
            return AgentTurnStatus.COMPLETED;
        }
        if (toolResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            return context != null && context.getPendingAction() != null
                    ? AgentTurnStatus.NEED_MORE_INFO
                    : AgentTurnStatus.FAILED;
        }
        return AgentTurnStatus.FAILED;
    }

    private String previewText(AgentIntent intent, AgentToolParameters parameters) {
        if (intent == AgentIntent.TRANSFER_TICKET) {
            return "转派工单#" + valueOrUnknown(parameters == null ? null : parameters.getTicketId())
                    + " -> 处理人#" + valueOrUnknown(parameters == null ? null : parameters.getAssigneeId());
        }
        return "创建工单：" + valueOrUnknown(parameters == null ? null : parameters.getTitle());
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "未识别" : String.valueOf(value);
    }
}
