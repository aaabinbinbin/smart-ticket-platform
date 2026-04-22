package com.smartticket.agent.service;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.support.SpringAiToolCallState;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.agent.tool.ticket.CreateTicketTool;
import com.smartticket.agent.tool.ticket.QueryTicketTool;
import com.smartticket.agent.tool.ticket.SearchHistoryTool;
import com.smartticket.agent.tool.ticket.TransferTicketTool;
import com.smartticket.biz.model.CurrentUser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 对话门面。
 *
 * <p>由路由器决定当前意图，再由 Spring AI Tool Calling 或确定性兜底链路执行对应工具。</p>
 */
@Service
public class AgentFacade {
    private static final Logger log = LoggerFactory.getLogger(AgentFacade.class);

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final boolean chatEnabled;
    private final IntentRouter intentRouter;
    private final AgentSessionService sessionService;
    private final AgentExecutionGuard executionGuard;
    private final AgentToolParameterExtractor parameterExtractor;
    private final QueryTicketTool queryTicketTool;
    private final CreateTicketTool createTicketTool;
    private final TransferTicketTool transferTicketTool;
    private final SearchHistoryTool searchHistoryTool;

    public AgentFacade(
            ObjectProvider<ChatClient> chatClientProvider,
            @Value("${smart-ticket.ai.chat.enabled:false}") boolean chatEnabled,
            IntentRouter intentRouter,
            AgentSessionService sessionService,
            AgentExecutionGuard executionGuard,
            AgentToolParameterExtractor parameterExtractor,
            QueryTicketTool queryTicketTool,
            CreateTicketTool createTicketTool,
            TransferTicketTool transferTicketTool,
            SearchHistoryTool searchHistoryTool
    ) {
        this.chatClientProvider = chatClientProvider;
        this.chatEnabled = chatEnabled;
        this.intentRouter = intentRouter;
        this.sessionService = sessionService;
        this.executionGuard = executionGuard;
        this.parameterExtractor = parameterExtractor;
        this.queryTicketTool = queryTicketTool;
        this.createTicketTool = createTicketTool;
        this.transferTicketTool = transferTicketTool;
        this.searchHistoryTool = searchHistoryTool;
    }

    public AgentChatResult chat(CurrentUser currentUser, String sessionId, String message) {
        boolean springAiReady = isSpringAiChatReady();
        AgentSessionContext context = sessionService.load(sessionId);
        if (hasPendingCreateDraft(context)) {
            return continuePendingCreate(currentUser, sessionId, message, context, springAiReady);
        }

        IntentRoute route = intentRouter.route(message, context);
        log.info("agent facade chat: sessionId={}, userId={}, intent={}, springAiChatReady={}",
                sessionId, currentUser.getUserId(), route.getIntent(), springAiReady);

        if (route.getConfidence() < 0.50d) {
            return clarifyLowConfidenceIntent(sessionId, message, context, route, springAiReady);
        }

        if (springAiReady) {
            Optional<AgentChatResult> springAiResult = trySpringAiToolCalling(currentUser, sessionId, message, context, route);
            if (springAiResult.isPresent()) {
                return springAiResult.get();
            }
        }
        return executeDeterministicFallback(currentUser, sessionId, message, context, route, springAiReady);
    }

    private AgentChatResult clarifyLowConfidenceIntent(
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            boolean springAiReady
    ) {
        AgentToolResult toolResult = AgentToolResult.builder()
                .invoked(false)
                .toolName("clarifyIntent")
                .reply("我暂时无法判断你的目标。请明确说明你是想查询工单、创建工单、转派工单，还是检索历史案例。")
                .build();
        sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady);
    }

    private Optional<AgentChatResult> trySpringAiToolCalling(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route
    ) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return Optional.empty();
        }

        SpringAiToolCallState state = new SpringAiToolCallState();
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(SpringAiToolSupport.CURRENT_USER_KEY, currentUser);
        toolContext.put(SpringAiToolSupport.SESSION_CONTEXT_KEY, context);
        toolContext.put(SpringAiToolSupport.MESSAGE_KEY, message);
        toolContext.put(SpringAiToolSupport.ROUTE_KEY, route);
        toolContext.put(SpringAiToolSupport.STATE_KEY, state);

        try {
            String content = chatClient.prompt()
                    .system(systemPrompt(route.getIntent()))
                    .user(userPrompt(message, route))
                    .tools(springAiToolFor(route.getIntent()))
                    .toolContext(toolContext)
                    .call()
                    .content();
            AgentToolResult toolResult = state.getResult();
            if (toolResult == null) {
                log.info("spring ai tool calling produced no tool result, use deterministic fallback: sessionId={}", sessionId);
                return Optional.empty();
            }
            syncCreatePendingAction(context, route, null, message, toolResult);
            sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
            return Optional.of(toChatResult(
                    sessionId,
                    route,
                    context,
                    toolResult,
                    hasText(content) ? content : toolResult.getReply(),
                    true
            ));
        } catch (RuntimeException ex) {
            log.warn("spring ai tool calling failed, use deterministic fallback: sessionId={}, reason={}", sessionId, ex.getMessage());
            return Optional.empty();
        }
    }

    private AgentChatResult executeDeterministicFallback(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            boolean springAiReady
    ) {
        AgentTool tool = agentToolFor(route.getIntent());
        AgentToolParameters parameters = parameterExtractor.extract(message, context);
        sessionService.resolveReferences(message, context, parameters);
        ToolCallPlan plan = ToolCallPlan.builder()
                .intent(route.getIntent())
                .toolName(tool.name())
                .parameters(parameters)
                .llmGenerated(false)
                .reason("Spring AI ChatClient unavailable or no tool call")
                .build();

        AgentExecutionDecision decision = executionGuard.check(currentUser, message, context, route, plan);
        AgentToolResult toolResult;
        if (decision.isAllowed()) {
            toolResult = decision.getTool().execute(AgentToolRequest.builder()
                    .currentUser(currentUser)
                    .message(message)
                    .context(context)
                    .route(route)
                    .parameters(parameters)
                    .build());
        } else {
            toolResult = decision.toToolResult(tool.name());
        }
        syncCreatePendingAction(context, route, parameters, message, toolResult);
        sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady);
    }

    private AgentChatResult continuePendingCreate(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            boolean springAiReady
    ) {
        IntentRoute route = IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.99d)
                .reason("继续补全待创建工单草稿")
                .build();
        if (isCancelMessage(message)) {
            context.setPendingAction(null);
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(createTicketTool.name())
                    .reply("已取消本次工单创建。你可以随时重新发起新的创建请求。")
                    .build();
            sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
            return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady);
        }

        AgentPendingAction pendingAction = context.getPendingAction();
        AgentToolParameters mergedParameters = mergeCreateDraftParameters(
                pendingAction.getPendingParameters(),
                parameterExtractor.extract(message, context),
                message,
                pendingAction.getAwaitingFields()
        );
        AgentToolResult toolResult = createTicketTool.execute(AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(mergedParameters)
                .build());
        syncCreatePendingAction(context, route, mergedParameters, message, toolResult);
        sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady);
    }

    private boolean hasPendingCreateDraft(AgentSessionContext context) {
        return context != null
                && context.getPendingAction() != null
                && context.getPendingAction().getPendingIntent() == AgentIntent.CREATE_TICKET;
    }

    private void syncCreatePendingAction(
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters parameters,
            String message,
            AgentToolResult toolResult
    ) {
        if (context == null || route.getIntent() != AgentIntent.CREATE_TICKET) {
            return;
        }
        if (toolResult.getStatus() == AgentToolStatus.SUCCESS) {
            context.setPendingAction(null);
            return;
        }
        if (toolResult.getStatus() != AgentToolStatus.NEED_MORE_INFO) {
            return;
        }
        List<AgentToolParameterField> missingFields = extractMissingFields(toolResult.getData());
        AgentToolParameters draft = parameters == null ? AgentToolParameters.builder().build() : copyParameters(parameters);
        context.setPendingAction(AgentPendingAction.builder()
                .pendingIntent(AgentIntent.CREATE_TICKET)
                .pendingToolName(createTicketTool.name())
                .pendingParameters(draft)
                .awaitingFields(missingFields)
                .lastToolResult(toolResult)
                .build());
        toolResult.setReply(buildCreateClarificationReply(missingFields, draft, message));
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
        merged.setNumbers(extracted.getNumbers() == null ? List.of() : extracted.getNumbers());
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

    private AgentToolParameters copyParameters(AgentToolParameters source) {
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

    private Object springAiToolFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> createTicketTool;
            case TRANSFER_TICKET -> transferTicketTool;
            case SEARCH_HISTORY -> searchHistoryTool;
            case QUERY_TICKET -> queryTicketTool;
        };
    }

    private AgentTool agentToolFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> createTicketTool;
            case TRANSFER_TICKET -> transferTicketTool;
            case SEARCH_HISTORY -> searchHistoryTool;
            case QUERY_TICKET -> queryTicketTool;
        };
    }

    private AgentChatResult toChatResult(
            String sessionId,
            IntentRoute route,
            AgentSessionContext context,
            AgentToolResult toolResult,
            String reply,
            boolean springAiReady
    ) {
        return AgentChatResult.builder()
                .sessionId(sessionId)
                .intent(route.getIntent().name())
                .reply(hasText(reply) ? reply : toolResult.getReply())
                .route(route)
                .context(context)
                .result(toolResult.getData())
                .springAiChatReady(springAiReady)
                .build();
    }

    private String systemPrompt(AgentIntent intent) {
        return switch (intent) {
            case QUERY_TICKET -> "你是工单查询助手。本轮只允许调用 queryTicket，用于查询当前工单事实，不得检索历史知识库。";
            case CREATE_TICKET -> "你是工单创建助手。本轮只允许调用 createTicket。创建动作必须通过工具完成，不能编造创建结果。";
            case TRANSFER_TICKET -> "你是工单转派助手。本轮只允许调用 transferTicket。转派是高风险写操作，必须遵守工具返回的确认或失败信息。";
            case SEARCH_HISTORY -> "你是历史经验检索助手。本轮只允许调用 searchHistory。检索结果只作参考，不代表当前工单事实。";
        };
    }

    private String userPrompt(String message, IntentRoute route) {
        return """
                本轮意图：%s
                路由原因：%s
                用户消息：%s

                请根据用户消息提取工具参数并调用本轮提供的工具。不要调用未提供的工具。
                """.formatted(route.getIntent().name(), route.getReason(), message);
    }

    private boolean isSpringAiChatReady() {
        return chatEnabled && chatClientProvider.getIfAvailable() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
