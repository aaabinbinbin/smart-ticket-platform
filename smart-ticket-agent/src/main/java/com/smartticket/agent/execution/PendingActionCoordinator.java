package com.smartticket.agent.execution;

import com.smartticket.agent.command.AgentCommandHandler;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.AgentExecutionSummary;
import com.smartticket.agent.orchestration.AgentTurnStatus;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PendingAction 协调器。
 *
 * <p>该类位于 Agent 主链的 pendingAction 统一入口，负责补参草稿和高风险确认两类待办状态的创建、
 * 恢复、取消和清理。它只修改内存中的 session context，并返回结构化执行摘要，不保存 session、
 * 不写 memory、不过问 trace，也不会改变 /api/agent/chat 的接口协议。</p>
 */
@Service
public class PendingActionCoordinator {
    /**
     * pendingAction 默认有效期。
     *
     * <p>高风险确认和补参草稿都不应长期有效，避免用户隔很久后确认旧命令导致误写库。</p>
     */
    private static final Duration PENDING_ACTION_TTL = Duration.ofMinutes(30);

    private final AgentPlanner agentPlanner;
    private final AgentToolParameterExtractor parameterExtractor;
    private final Map<AgentIntent, AgentCommandHandler> handlersByIntent;

    public PendingActionCoordinator(
            AgentPlanner agentPlanner,
            AgentToolParameterExtractor parameterExtractor,
            List<AgentCommandHandler> commandHandlers
    ) {
        this.agentPlanner = agentPlanner;
        this.parameterExtractor = parameterExtractor;
        this.handlersByIntent = buildHandlerIndex(commandHandlers);
    }

    /**
     * 判断当前会话是否存在待恢复的 pendingAction。
     *
     * <p>AgentFacade 只能通过这个入口判断是否进入补参/确认续办路径，避免外部重新散落判断逻辑。</p>
     *
     * @param context 当前会话上下文
     * @return true 表示本轮应继续上一轮未完成操作
     */
    public boolean hasPendingAction(AgentSessionContext context) {
        return context != null && context.getPendingAction() != null;
    }

    /**
     * 继续处理当前 session 中尚未完成的 pendingAction。
     *
     * <p>该方法会恢复对应 route/plan，并在需要时执行 Tool 或取消待办；但不会保存 session、
     * 不会写 memory，也不会记录 trace。调用方必须在拿到结果后统一提交本轮状态。</p>
     *
     * @param currentUser 当前用户
     * @param message 当前轮用户消息
     * @param context 当前会话上下文
     * @return 已恢复的 route/plan 和统一执行摘要
     */
    public PendingContinuation continuePendingAction(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context
    ) {
        if (!hasPendingAction(context)) {
            throw new IllegalStateException("当前会话不存在 pendingAction，不能继续恢复");
        }
        AgentPendingAction pendingAction = context.getPendingAction();
        if (pendingAction.isExpired(LocalDateTime.now())) {
            return expirePendingAction(message, context, pendingAction);
        }
        if (pendingAction.isAwaitingConfirmation()) {
            return continuePendingConfirmation(currentUser, message, context, pendingAction);
        }
        return continuePendingCreate(currentUser, message, context, pendingAction);
    }

    /**
     * 为高风险写操作创建确认态 pendingAction。
     *
     * <p>这是主链创建确认 pending 的唯一入口。该方法会直接更新 context 中的 pendingAction，
     * 并返回与旧行为兼容的确认回复结果。</p>
     *
     * @param context 当前会话上下文
     * @param route 当前路由结果
     * @param parameters 已解析出的结构化参数
     * @param toolName 对应工具名
     * @param reason 需要确认的原因
     * @return 与确认态对应的 ToolResult
     */
    public AgentToolResult prepareConfirmation(
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters parameters,
            String toolName,
            String reason
    ) {
        AgentToolResult toolResult = buildConfirmationResult(toolName, route, parameters, reason);
        if (context != null) {
            // 高风险写操作必须先进入确认态，避免未确认时直接继续执行。
            context.setPendingAction(buildConfirmationPendingAction(route, parameters, toolName, toolResult));
        }
        return toolResult;
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

    /**
     * 根据本轮 Tool 结果同步 pendingAction。
     *
     * <p>当前 P2 只处理 CREATE_TICKET 补参草稿。成功时清理 pending，继续缺参时更新草稿，
     * 其他场景保持不变，避免提前引入 P3 之后的统一写命令链路。</p>
     *
     * @param context 当前会话上下文
     * @param route 当前路由结果
     * @param parameters 当前轮参数
     * @param result 当前轮 Tool 结果
     * @param message 当前用户消息
     */
    public void syncPendingAction(
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters parameters,
            AgentToolResult result,
            String message
    ) {
        if (context == null || route == null || route.getIntent() != AgentIntent.CREATE_TICKET || result == null) {
            return;
        }
        if (result.getStatus() == AgentToolStatus.SUCCESS) {
            clearPendingAction(context);
            return;
        }
        if (result.getStatus() != AgentToolStatus.NEED_MORE_INFO) {
            return;
        }
        List<AgentToolParameterField> missingFields = extractMissingFields(result.getData());
        AgentPendingAction pendingAction = buildCreateDraftPendingAction(parameters, result, missingFields);
        context.setPendingAction(pendingAction);
        result.setReply(buildCreateClarificationReply(missingFields, pendingAction.getPendingParameters(), message));
    }

    private PendingContinuation continuePendingConfirmation(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            AgentPendingAction pendingAction
    ) {
        IntentRoute route = IntentRoute.builder()
                .intent(pendingAction.getPendingIntent())
                .confidence(0.99d)
                .reason("继续等待高风险操作确认")
                .build();
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);

        if (isCancelMessage(message)) {
            clearPendingAction(context);
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(pendingAction.getPendingToolName())
                    .reply("已取消本次高风险操作，未执行任何变更。")
                    .build();
            agentPlanner.afterTool(plan, toolResult);
            return buildContinuation(route, plan, AgentExecutionSummary.builder()
                    .status(AgentTurnStatus.CANCELLED)
                    .mode(AgentExecutionMode.PENDING_CONTINUATION)
                    .intent(route.getIntent())
                    .parameters(pendingAction.getPendingParameters())
                    .primaryResult(toolResult)
                    .springAiUsed(false)
                    .fallbackUsed(false)
                    .toolInvoked(false)
                    .build());
        }

        if (!isConfirmMessage(message)) {
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.NEED_MORE_INFO)
                    .toolName(pendingAction.getPendingToolName())
                    .reply(pendingAction.getConfirmationSummary() + "\n\n请回复「确认执行」继续，或回复「取消」放弃。")
                    .build();
            return buildContinuation(route, plan, AgentExecutionSummary.builder()
                    .status(AgentTurnStatus.NEED_CONFIRMATION)
                    .mode(AgentExecutionMode.PENDING_CONTINUATION)
                    .intent(route.getIntent())
                    .parameters(pendingAction.getPendingParameters())
                    .primaryResult(toolResult)
                    .pendingAction(context.getPendingAction())
                    .springAiUsed(false)
                    .fallbackUsed(false)
                    .toolInvoked(false)
                    .build());
        }

        if (!canConfirmHighRiskAction(currentUser, route.getIntent())) {
            clearPendingAction(context);
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(pendingAction.getPendingToolName())
                    .reply("当前用户无权执行该高风险操作，未执行任何变更。")
                    .build();
            agentPlanner.afterTool(plan, toolResult);
            return buildContinuation(route, plan, AgentExecutionSummary.builder()
                    .status(AgentTurnStatus.FAILED)
                    .mode(AgentExecutionMode.PENDING_CONTINUATION)
                    .intent(route.getIntent())
                    .parameters(pendingAction.getPendingParameters())
                    .primaryResult(toolResult)
                    .springAiUsed(false)
                    .fallbackUsed(false)
                    .toolInvoked(false)
                    .build());
        }

        AgentCommandHandler handler = requireHandler(route.getIntent());
        AgentToolParameters parameters = copyParameters(pendingAction.getPendingParameters());
        clearPendingAction(context);
        AgentToolResult toolResult;
        try {
            toolResult = handler.execute(currentUser, message, context, route, parameters);
        } catch (RuntimeException ex) {
            // 确认后仍要服从底层业务权限和状态校验；任何拒绝都转成失败摘要，避免旧 pending 被误认为已执行。
            toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(pendingAction.getPendingToolName())
                    .reply("当前用户无权或当前状态不允许执行该操作，未执行任何变更。")
                    .data(ex.getMessage())
                    .build();
        }
        agentPlanner.afterTool(plan, toolResult);
        return buildContinuation(route, plan, AgentExecutionSummary.builder()
                .status(summarizeToolStatus(toolResult))
                .mode(AgentExecutionMode.PENDING_CONTINUATION)
                .intent(route.getIntent())
                .parameters(parameters)
                .primaryResult(toolResult)
                .springAiUsed(false)
                .fallbackUsed(false)
                .toolInvoked(toolResult.isInvoked())
                .build());
    }

    /**
     * 清理并返回过期 pendingAction 的安全结果。
     *
     * <p>过期待办不能继续执行，即使用户发送确认消息也必须停止，避免旧的高风险写命令在上下文变化后被误触发。</p>
     */
    private PendingContinuation expirePendingAction(
            String message,
            AgentSessionContext context,
            AgentPendingAction pendingAction
    ) {
        IntentRoute route = IntentRoute.builder()
                .intent(pendingAction.getPendingIntent())
                .confidence(0.99d)
                .reason("pendingAction 已过期")
                .build();
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);
        // 过期 pending 必须先清理再返回，防止用户连续发送“确认”时复用旧写命令。
        clearPendingAction(context);
        AgentToolResult toolResult = AgentToolResult.builder()
                .invoked(false)
                .status(AgentToolStatus.FAILED)
                .toolName(pendingAction.getPendingToolName())
                .reply("上一轮待确认操作已过期，未执行任何变更。请重新发起请求。")
                .data(message)
                .build();
        agentPlanner.afterTool(plan, toolResult);
        return buildContinuation(route, plan, AgentExecutionSummary.builder()
                .status(AgentTurnStatus.CANCELLED)
                .mode(AgentExecutionMode.PENDING_CONTINUATION)
                .intent(route.getIntent())
                .parameters(pendingAction.getPendingParameters())
                .primaryResult(toolResult)
                .springAiUsed(false)
                .fallbackUsed(false)
                .toolInvoked(false)
                .build());
    }

    private PendingContinuation continuePendingCreate(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            AgentPendingAction pendingAction
    ) {
        IntentRoute route = IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.99d)
                .reason("继续补全待创建工单草稿")
                .build();
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);

        if (isCancelMessage(message)) {
            clearPendingAction(context);
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(requireHandler(AgentIntent.CREATE_TICKET).toolName())
                    .reply("已取消本次工单创建。你可以随时重新发起新的创建请求。")
                    .build();
            agentPlanner.afterTool(plan, toolResult);
            return buildContinuation(route, plan, AgentExecutionSummary.builder()
                    .status(AgentTurnStatus.CANCELLED)
                    .mode(AgentExecutionMode.PENDING_CONTINUATION)
                    .intent(route.getIntent())
                    .primaryResult(toolResult)
                    .springAiUsed(false)
                    .fallbackUsed(false)
                    .toolInvoked(false)
                    .build());
        }

        AgentCommandHandler handler = requireHandler(AgentIntent.CREATE_TICKET);
        AgentToolParameters mergedParameters = mergeCreateDraftParameters(
                pendingAction.getPendingParameters(),
                parameterExtractor.extract(message, context),
                message,
                pendingAction.getAwaitingFields()
        );
        AgentToolResult toolResult = handler.execute(currentUser, message, context, route, mergedParameters);
        syncPendingAction(context, route, mergedParameters, toolResult, message);
        agentPlanner.afterTool(plan, toolResult);
        return buildContinuation(route, plan, AgentExecutionSummary.builder()
                .status(summarizeCreateContinuationStatus(context, toolResult))
                .mode(AgentExecutionMode.PENDING_CONTINUATION)
                .intent(route.getIntent())
                .parameters(mergedParameters)
                .primaryResult(toolResult)
                .pendingAction(context.getPendingAction())
                .springAiUsed(false)
                .fallbackUsed(false)
                .toolInvoked(toolResult.isInvoked())
                .build());
    }

    private PendingContinuation buildContinuation(
            IntentRoute route,
            AgentPlan plan,
            AgentExecutionSummary summary
    ) {
        return PendingContinuation.builder()
                .route(route)
                .plan(plan)
                .summary(summary)
                .build();
    }

    private AgentPendingAction buildConfirmationPendingAction(
            IntentRoute route,
            AgentToolParameters parameters,
            String toolName,
            AgentToolResult toolResult
    ) {
        LocalDateTime now = LocalDateTime.now();
        return AgentPendingAction.builder()
                .pendingIntent(route.getIntent())
                .pendingToolName(toolName)
                .pendingParameters(copyParameters(parameters))
                .awaitingConfirmation(true)
                .confirmationSummary(toolResult.getReply())
                .lastToolResult(toolResult)
                .createdAt(now)
                .expiresAt(now.plus(PENDING_ACTION_TTL))
                .build();
    }

    private AgentPendingAction buildCreateDraftPendingAction(
            AgentToolParameters parameters,
            AgentToolResult toolResult,
            List<AgentToolParameterField> missingFields
    ) {
        AgentToolParameters draft = parameters == null ? AgentToolParameters.builder().build() : copyParameters(parameters);
        LocalDateTime now = LocalDateTime.now();
        return AgentPendingAction.builder()
                .pendingIntent(AgentIntent.CREATE_TICKET)
                .pendingToolName(requireHandler(AgentIntent.CREATE_TICKET).toolName())
                .pendingParameters(draft)
                .awaitingFields(missingFields)
                .lastToolResult(toolResult)
                .createdAt(now)
                .expiresAt(now.plus(PENDING_ACTION_TTL))
                .build();
    }

    private AgentToolResult buildConfirmationResult(
            String toolName,
            IntentRoute route,
            AgentToolParameters parameters,
            String reason
    ) {
        String summary = switch (route.getIntent()) {
            case TRANSFER_TICKET -> "高风险操作需要确认。\n"
                    + "操作：转派工单\n"
                    + "工单 ID：" + valueOrUnknown(parameters == null ? null : parameters.getTicketId()) + "\n"
                    + "目标处理人 ID：" + valueOrUnknown(parameters == null ? null : parameters.getAssigneeId()) + "\n"
                    + "原因：" + reason + "\n"
                    + "请回复「确认执行」继续，或回复「取消」放弃。";
            default -> "该操作风险较高，需要二次确认后才能执行。请回复「确认执行」继续，或回复「取消」放弃。";
        };
        return AgentToolResult.builder()
                .invoked(false)
                .status(AgentToolStatus.NEED_MORE_INFO)
                .toolName(toolName)
                .reply(summary)
                .data(parameters)
                .activeTicketId(parameters == null ? null : parameters.getTicketId())
                .activeAssigneeId(parameters == null ? null : parameters.getAssigneeId())
                .build();
    }

    private List<AgentToolParameterField> extractMissingFields(Object data) {
        if (!(data instanceof List<?> rawList)) {
            return List.of();
        }
        List<AgentToolParameterField> fields = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof AgentToolParameterField field) {
                fields.add(field);
            }
        }
        return fields;
    }

    private AgentToolParameters mergeCreateDraftParameters(
            AgentToolParameters draft,
            AgentToolParameters extracted,
            String message,
            List<AgentToolParameterField> awaitingFields
    ) {
        AgentToolParameters merged = copyParameters(draft == null ? AgentToolParameters.builder().build() : draft);
        if (extracted == null) {
            return merged;
        }
        if (extracted.getType() != null) {
            merged.setType(extracted.getType());
        }
        if (extracted.getCategory() != null) {
            merged.setCategory(extracted.getCategory());
        }
        if (extracted.getPriority() != null) {
            merged.setPriority(extracted.getPriority());
        }
        boolean metadataOnly = isLikelyMetadataOnlyMessage(extracted, message);
        if (!metadataOnly && shouldFillTitle(merged, awaitingFields, extracted)) {
            merged.setTitle(extracted.getTitle());
        }
        if (!metadataOnly && shouldFillDescription(merged, awaitingFields, extracted, message)) {
            merged.setDescription(message == null ? null : message.trim());
        }
        if (hasText(extracted.getIdempotencyKey())) {
            merged.setIdempotencyKey(extracted.getIdempotencyKey());
        }
        merged.setNumbers(extracted.getNumbers() == null ? List.of() : new ArrayList<>(extracted.getNumbers()));
        return merged;
    }

    private boolean shouldFillTitle(
            AgentToolParameters merged,
            List<AgentToolParameterField> awaitingFields,
            AgentToolParameters extracted
    ) {
        return hasText(extracted.getTitle())
                && (!hasText(merged.getTitle()) || awaitingFields.contains(AgentToolParameterField.TITLE));
    }

    private boolean shouldFillDescription(
            AgentToolParameters merged,
            List<AgentToolParameterField> awaitingFields,
            AgentToolParameters extracted,
            String message
    ) {
        return hasText(message)
                && (!hasText(merged.getDescription())
                || awaitingFields.contains(AgentToolParameterField.DESCRIPTION)
                || extracted.getDescription() != null);
    }

    private boolean isLikelyMetadataOnlyMessage(AgentToolParameters extracted, String message) {
        if (!hasText(message)) {
            return false;
        }
        String trimmed = message.trim();
        return trimmed.length() <= 20
                && (extracted.getType() != null || extracted.getCategory() != null || extracted.getPriority() != null)
                && !containsProblemNarrative(trimmed);
    }

    private boolean containsProblemNarrative(String message) {
        return message.contains("无法")
                || message.contains("失败")
                || message.contains("异常")
                || message.contains("报错")
                || message.contains("问题")
                || message.contains("影响")
                || message.toLowerCase().contains("error");
    }

    private String buildCreateClarificationReply(
            List<AgentToolParameterField> missingFields,
            AgentToolParameters draft,
            String message
    ) {
        if (missingFields.isEmpty()) {
            return "请继续补充创建工单所需的信息。";
        }
        if (missingFields.size() == 1) {
            AgentToolParameterField field = missingFields.get(0);
            return switch (field) {
                case TITLE -> "请补充工单标题，尽量用一句话说明核心问题。";
                case DESCRIPTION -> "请补充更完整的问题描述，例如现象、影响范围和你已尝试过的操作。";
                case CATEGORY -> "请补充工单分类：ACCOUNT、SYSTEM、ENVIRONMENT、OTHER。";
                case PRIORITY -> "请补充工单优先级：LOW、MEDIUM、HIGH、URGENT。";
                default -> "请补充" + field.getLabel() + "。";
            };
        }
        StringBuilder reply = new StringBuilder("我先记录了当前工单草稿");
        if (hasText(draft.getTitle())) {
            reply.append("（标题：").append(draft.getTitle()).append("）");
        }
        reply.append("。还需要补充：");
        for (int i = 0; i < missingFields.size(); i++) {
            if (i > 0) {
                reply.append("、");
            }
            reply.append(missingFields.get(i).getLabel());
        }
        reply.append("。消息内容：").append(message == null ? "" : message.trim());
        return reply.toString();
    }

    private AgentTurnStatus summarizeToolStatus(AgentToolResult toolResult) {
        if (toolResult == null) {
            return AgentTurnStatus.FAILED;
        }
        if (toolResult.getStatus() == AgentToolStatus.SUCCESS) {
            return AgentTurnStatus.COMPLETED;
        }
        if (toolResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            return AgentTurnStatus.NEED_MORE_INFO;
        }
        return AgentTurnStatus.FAILED;
    }

    private AgentTurnStatus summarizeCreateContinuationStatus(
            AgentSessionContext context,
            AgentToolResult toolResult
    ) {
        if (toolResult == null) {
            return AgentTurnStatus.FAILED;
        }
        if (toolResult.getStatus() == AgentToolStatus.SUCCESS) {
            return AgentTurnStatus.COMPLETED;
        }
        if (toolResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            return context != null && context.getPendingAction() != null
                    ? AgentTurnStatus.NEED_MORE_INFO
                    : AgentTurnStatus.COMPLETED;
        }
        return AgentTurnStatus.FAILED;
    }

    private void clearPendingAction(AgentSessionContext context) {
        if (context != null) {
            context.setPendingAction(null);
        }
    }

    private AgentToolParameters copyParameters(AgentToolParameters source) {
        if (source == null) {
            return AgentToolParameters.builder().build();
        }
        return AgentToolParameters.builder()
                .ticketId(source.getTicketId())
                .assigneeId(source.getAssigneeId())
                .title(source.getTitle())
                .description(source.getDescription())
                .idempotencyKey(source.getIdempotencyKey())
                .type(source.getType())
                .category(source.getCategory())
                .priority(source.getPriority())
                .summaryRequested(source.getSummaryRequested())
                .summaryView(source.getSummaryView())
                .numbers(source.getNumbers() == null ? List.of() : new ArrayList<>(source.getNumbers()))
                .build();
    }

    private boolean isCancelMessage(String message) {
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.contains("取消")
                || normalized.contains("不用了")
                || normalized.contains("算了")
                || normalized.equals("cancel");
    }

    private boolean isConfirmMessage(String message) {
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.contains("确认")
                || normalized.contains("同意")
                || normalized.contains("执行")
                || normalized.contains("confirm")
                || normalized.equals("yes");
    }

    /**
     * 对高风险确认做最小角色闸门。
     *
     * <p>这里不是替代 biz 层的数据权限校验，只是避免普通 USER 通过旧 pendingAction 进入写工具。
     * 真正的工单关系权限仍由 TicketWorkflowService 在执行时二次校验。</p>
     */
    private boolean canConfirmHighRiskAction(CurrentUser currentUser, AgentIntent intent) {
        if (intent != AgentIntent.TRANSFER_TICKET) {
            return true;
        }
        if (currentUser == null || currentUser.getRoles() == null) {
            return false;
        }
        // 转派是高风险写操作，确认环节先做最小角色闸门，后续仍由 biz 层按工单关系做精确权限判断。
        return currentUser.getRoles().contains("ADMIN") || currentUser.getRoles().contains("STAFF");
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "未识别" : String.valueOf(value);
    }

    private AgentCommandHandler requireHandler(AgentIntent intent) {
        AgentCommandHandler handler = handlersByIntent.get(intent);
        if (handler == null) {
            throw new IllegalArgumentException("未找到写命令处理器: " + intent);
        }
        return handler;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * PendingAction 续办结果。
     *
     * <p>该对象把恢复后的 route、plan 和统一执行摘要一起返回给 AgentFacade，便于外层统一渲染回复、
     * 提交 session/memory 和记录 trace，而不会把 pending 细节重新散落回 Facade。</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingContinuation {
        /**
         * 当前待办恢复后的路由结果。
         */
        private IntentRoute route;

        /**
         * 当前待办恢复后的计划状态。
         */
        private AgentPlan plan;

        /**
         * 当前待办恢复后的统一执行摘要。
         */
        private AgentExecutionSummary summary;
    }
}
