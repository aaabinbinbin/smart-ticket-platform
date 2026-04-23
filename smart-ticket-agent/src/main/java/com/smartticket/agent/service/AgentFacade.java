package com.smartticket.agent.service;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionDecisionStatus;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.memory.AgentMemoryService;
import com.smartticket.agent.model.AgentChatResult;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.orchestration.ToolCallPlan;
import com.smartticket.agent.planner.AgentPlan;
import com.smartticket.agent.planner.AgentPlanner;
import com.smartticket.agent.prompt.PromptTemplateService;
import com.smartticket.agent.skill.AgentSkill;
import com.smartticket.agent.skill.SkillRegistry;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.support.SpringAiToolCallState;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.agent.trace.AgentTraceContext;
import com.smartticket.agent.trace.AgentTraceService;
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
 * Agent 闁诲海鏁搁、濠囨儊娴犲鈷掗柕濞炬櫆濡椼劑鏌? *
 * <p>闂佹眹鍨肩划楣冩儗妤ｅ啯鍋ㄩ柛妤冨仦閻濄倝鏌涢幇顒€鏆遍柣锝嗗礃閵囨劙骞橀崘宸瀫闂佽婢樼换鎰瑰鈧弫宥囦沪閽樺娅冮梺?Spring AI Tool Calling 闂佺懓鐡ㄩ悧婊堝灳濡皷鍋撶憴鍕暡闁逞屽劯閸愩劌绠查柟鐓庣摠濞茬喖骞楅懖鈺傚磯妞ゆ牗绋戦埛鏃堟偠濞戞瀚版い鏇ㄥ枟閹棃寮径鍝ョ煑闂佺绻戦悘姘跺焵?/p>
 */
@Service
public class AgentFacade {
    private static final Logger log = LoggerFactory.getLogger(AgentFacade.class);

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final boolean chatEnabled;
    private final IntentRouter intentRouter;
    private final AgentSessionService sessionService;
    private final AgentExecutionGuard executionGuard;
    private final AgentPlanner agentPlanner;
    private final SkillRegistry skillRegistry;
    private final AgentMemoryService memoryService;
    private final AgentTraceService traceService;
    private final PromptTemplateService promptTemplateService;
    private final AgentToolParameterExtractor parameterExtractor;

    public AgentFacade(
            ObjectProvider<ChatClient> chatClientProvider,
            @Value("${smart-ticket.ai.chat.enabled:false}") boolean chatEnabled,
            IntentRouter intentRouter,
            AgentSessionService sessionService,
            AgentExecutionGuard executionGuard,
            AgentPlanner agentPlanner,
            SkillRegistry skillRegistry,
            AgentMemoryService memoryService,
            AgentTraceService traceService,
            PromptTemplateService promptTemplateService,
            AgentToolParameterExtractor parameterExtractor
    ) {
        this.chatClientProvider = chatClientProvider;
        this.chatEnabled = chatEnabled;
        this.intentRouter = intentRouter;
        this.sessionService = sessionService;
        this.executionGuard = executionGuard;
        this.agentPlanner = agentPlanner;
        this.skillRegistry = skillRegistry;
        this.memoryService = memoryService;
        this.traceService = traceService;
        this.promptTemplateService = promptTemplateService;
        this.parameterExtractor = parameterExtractor;
    }

    public AgentChatResult chat(CurrentUser currentUser, String sessionId, String message) {
        boolean springAiReady = isSpringAiChatReady();
        AgentTraceContext trace = traceService.start(currentUser, sessionId, message);
        AgentSessionContext context = sessionService.load(sessionId);
        memoryService.hydrate(currentUser, context);
        if (hasPendingConfirmation(context)) {
            return continuePendingConfirmation(currentUser, sessionId, message, context, springAiReady, trace);
        }
        if (hasPendingCreateDraft(context)) {
            return continuePendingCreate(currentUser, sessionId, message, context, springAiReady, trace);
        }

        traceService.step(trace, "route", "before", null, "START", message);
        IntentRoute route = intentRouter.route(message, context);
        traceService.step(trace, "route", "after", null, route.getIntent().name(), String.valueOf(route.getConfidence()));
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);
        traceService.step(trace, "planner", "decision", plan.getNextSkillCode(), plan.getNextAction().name(), plan.getCurrentStage().name());
        log.info("agent facade chat: sessionId={}, userId={}, intent={}, springAiChatReady={}",
                sessionId, currentUser.getUserId(), route.getIntent(), springAiReady);

        if (route.getConfidence() < 0.50d) {
            return clarifyLowConfidenceIntent(currentUser, sessionId, message, context, route, plan, springAiReady, trace);
        }

        if (springAiReady) {
            Optional<AgentChatResult> springAiResult = trySpringAiToolCalling(currentUser, sessionId, message, context, route, plan, trace);
            if (springAiResult.isPresent()) {
                return springAiResult.get();
            }
        }
        return executeDeterministicFallback(currentUser, sessionId, message, context, route, plan, springAiReady, trace);
    }

    private AgentChatResult clarifyLowConfidenceIntent(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        agentPlanner.markClarify(plan, route.getReason());
        AgentToolResult toolResult = AgentToolResult.builder()
                .invoked(false)
                .toolName("clarifyIntent")
                .reply("\u6211\u6682\u65f6\u65e0\u6cd5\u5224\u65ad\u4f60\u7684\u76ee\u6807\u3002\u8bf7\u660e\u786e\u8bf4\u660e\u4f60\u662f\u60f3\u67e5\u8be2\u5de5\u5355\u3001\u521b\u5efa\u5de5\u5355\u3001\u8f6c\u6d3e\u5de5\u5355\uff0c\u8fd8\u662f\u68c0\u7d22\u5386\u53f2\u6848\u4f8b\u3002")
                .build();
        traceService.step(trace, "clarify", "reply", null, "NEED_USER", route.getReason());
        updateSessionAfterTool(currentUser, sessionId, context, route, message, null, toolResult);
        traceService.finish(trace, route, plan, null, toolResult, toolResult.getReply(), false, false);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
    }

    private Optional<AgentChatResult> trySpringAiToolCalling(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            AgentTraceContext trace
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
            agentPlanner.beforeExecute(plan);
            traceService.step(trace, "spring-ai", "tool-call", plan.getNextSkillCode(), "START", route.getIntent().name());
            String promptCode = promptCodeFor(route.getIntent());
            trace.setPromptVersion(promptCode + ":" + promptTemplateService.version(promptCode));
            traceService.step(trace, "prompt", "load", promptCode, trace.getPromptVersion(), "system prompt");
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
                traceService.step(trace, "spring-ai", "tool-call", plan.getNextSkillCode(), "NO_TOOL_RESULT", null);
                return Optional.empty();
            }
            syncCreatePendingAction(context, route, null, message, toolResult);
            agentPlanner.afterTool(plan, toolResult);
            updateSessionAfterTool(currentUser, sessionId, context, route, message, null, toolResult);
            traceService.step(trace, "spring-ai", "tool-call", toolResult.getToolName(), toolResult.getStatus().name(), "success");
            String finalReply = hasText(content) ? content : toolResult.getReply();
            traceService.finish(trace, route, plan, null, toolResult, finalReply, true, false);
            return Optional.of(toChatResult(
                    sessionId,
                    route,
                    context,
                    toolResult,
                    finalReply,
                    true,
                    plan,
                    trace
            ));
        } catch (RuntimeException ex) {
            log.warn("spring ai tool calling failed, use deterministic fallback: sessionId={}, reason={}", sessionId, ex.getMessage());
            traceService.step(trace, "spring-ai", "tool-call", plan.getNextSkillCode(), "FAILED", ex.getMessage());
            return Optional.empty();
        }
    }

    private AgentChatResult executeDeterministicFallback(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentPlan plan,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        AgentSkill skill = skillRegistry.requireByIntent(route.getIntent());
        AgentTool tool = skill.tool();
        AgentToolParameters parameters = parameterExtractor.extract(message, context);
        sessionService.resolveReferences(message, context, parameters);
        ToolCallPlan toolCallPlan = ToolCallPlan.builder()
                .intent(route.getIntent())
                .toolName(tool.name())
                .parameters(parameters)
                .llmGenerated(false)
                .reason("Spring AI ChatClient unavailable or no tool call")
                .build();

        agentPlanner.beforeExecute(plan);
        traceService.step(trace, "fallback", "skill-call", skill.skillCode(), "START", parameters.toString());
        AgentExecutionDecision decision = executionGuard.check(currentUser, message, context, route, toolCallPlan);
        AgentToolResult toolResult;
        if (decision.isAllowed()) {
            toolResult = decision.getTool().execute(AgentToolRequest.builder()
                    .currentUser(currentUser)
                    .message(message)
                    .context(context)
                    .route(route)
                    .parameters(parameters)
                    .build());
        } else if (decision.getStatus() == AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            toolResult = buildConfirmationResult(tool.name(), route, parameters, decision.getReason());
            context.setPendingAction(AgentPendingAction.builder()
                    .pendingIntent(route.getIntent())
                    .pendingToolName(tool.name())
                    .pendingParameters(copyParameters(parameters))
                    .awaitingConfirmation(true)
                    .confirmationSummary(toolResult.getReply())
                    .lastToolResult(toolResult)
                    .build());
            agentPlanner.markNeedConfirmation(plan, decision.getReason());
        } else {
            toolResult = decision.toToolResult(tool.name());
        }
        syncCreatePendingAction(context, route, parameters, message, toolResult);
        if (decision.getStatus() != AgentExecutionDecisionStatus.NEED_CONFIRMATION) {
            agentPlanner.afterTool(plan, toolResult);
        }
        updateSessionAfterTool(currentUser, sessionId, context, route, message, parameters, toolResult);
        traceService.step(trace, "fallback", "skill-call", toolResult.getToolName(), toolResult.getStatus().name(), "finished");
        traceService.finish(trace, route, plan, parameters, toolResult, toolResult.getReply(), false, true);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
    }

    private AgentChatResult continuePendingConfirmation(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        AgentPendingAction pendingAction = context.getPendingAction();
        IntentRoute route = IntentRoute.builder()
                .intent(pendingAction.getPendingIntent())
                .confidence(0.99d)
                .reason("\u7ee7\u7eed\u7b49\u5f85\u9ad8\u98ce\u9669\u64cd\u4f5c\u786e\u8ba4")
                .build();
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);
        traceService.step(trace, "human-confirmation", "pending", pendingAction.getPendingToolName(), "WAITING", pendingAction.getConfirmationSummary());

        if (isCancelMessage(message)) {
            context.setPendingAction(null);
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(pendingAction.getPendingToolName())
                    .reply("\u5df2\u53d6\u6d88\u672c\u6b21\u9ad8\u98ce\u9669\u64cd\u4f5c\uff0c\u672a\u6267\u884c\u4efb\u4f55\u53d8\u66f4\u3002")
                    .build();
            updateSessionAfterTool(currentUser, sessionId, context, route, message, pendingAction.getPendingParameters(), toolResult);
            agentPlanner.afterTool(plan, toolResult);
            traceService.finish(trace, route, plan, pendingAction.getPendingParameters(), toolResult, toolResult.getReply(), false, false);
            return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
        }

        if (!isConfirmMessage(message)) {
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.NEED_MORE_INFO)
                    .toolName(pendingAction.getPendingToolName())
                    .reply(pendingAction.getConfirmationSummary() + "\n\n\u8bf7\u56de\u590d\u201c\u786e\u8ba4\u6267\u884c\u201d\u7ee7\u7eed\uff0c\u6216\u56de\u590d\u201c\u53d6\u6d88\u201d\u653e\u5f03\u3002")
                    .build();
            updateSessionAfterTool(currentUser, sessionId, context, route, message, pendingAction.getPendingParameters(), toolResult);
            traceService.finish(trace, route, plan, pendingAction.getPendingParameters(), toolResult, toolResult.getReply(), false, false);
            return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
        }

        AgentTool tool = toolForName(pendingAction.getPendingToolName());
        AgentToolParameters parameters = copyParameters(pendingAction.getPendingParameters());
        context.setPendingAction(null);
        AgentToolResult toolResult = tool.execute(AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(parameters)
                .build());
        agentPlanner.afterTool(plan, toolResult);
        updateSessionAfterTool(currentUser, sessionId, context, route, message, parameters, toolResult);
        traceService.step(trace, "human-confirmation", "execute", tool.name(), toolResult.getStatus().name(), "confirmed");
        traceService.finish(trace, route, plan, parameters, toolResult, toolResult.getReply(), false, false);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
    }

    private AgentChatResult continuePendingCreate(
            CurrentUser currentUser,
            String sessionId,
            String message,
            AgentSessionContext context,
            boolean springAiReady,
            AgentTraceContext trace
    ) {
        IntentRoute route = IntentRoute.builder()
                .intent(AgentIntent.CREATE_TICKET)
                .confidence(0.99d)
                .reason("\u7ee7\u7eed\u8865\u5168\u5f85\u521b\u5efa\u5de5\u5355\u8349\u7a3f")
                .build();
        AgentPlan plan = agentPlanner.buildOrLoadPlan(context, route);
        context.setPlanState(plan);
        traceService.step(trace, "planner", "pending-create", plan.getNextSkillCode(), plan.getNextAction().name(), plan.getCurrentStage().name());
        if (isCancelMessage(message)) {
            context.setPendingAction(null);
            AgentToolResult toolResult = AgentToolResult.builder()
                    .invoked(false)
                    .status(AgentToolStatus.FAILED)
                    .toolName(toolForIntent(AgentIntent.CREATE_TICKET).name())
                    .reply("\u5df2\u53d6\u6d88\u672c\u6b21\u5de5\u5355\u521b\u5efa\u3002\u4f60\u53ef\u4ee5\u968f\u65f6\u91cd\u65b0\u53d1\u8d77\u65b0\u7684\u521b\u5efa\u8bf7\u6c42\u3002")
                    .build();
            updateSessionAfterTool(currentUser, sessionId, context, route, message, null, toolResult);
            agentPlanner.afterTool(plan, toolResult);
            traceService.finish(trace, route, plan, null, toolResult, toolResult.getReply(), false, false);
            return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
        }

        AgentTool createTool = toolForIntent(AgentIntent.CREATE_TICKET);
        AgentPendingAction pendingAction = context.getPendingAction();
        AgentToolParameters mergedParameters = mergeCreateDraftParameters(
                pendingAction.getPendingParameters(),
                parameterExtractor.extract(message, context),
                message,
                pendingAction.getAwaitingFields()
        );
        AgentToolResult toolResult = createTool.execute(AgentToolRequest.builder()
                .currentUser(currentUser)
                .message(message)
                .context(context)
                .route(route)
                .parameters(mergedParameters)
                .build());
        syncCreatePendingAction(context, route, mergedParameters, message, toolResult);
        agentPlanner.afterTool(plan, toolResult);
        updateSessionAfterTool(currentUser, sessionId, context, route, message, mergedParameters, toolResult);
        traceService.finish(trace, route, plan, mergedParameters, toolResult, toolResult.getReply(), false, false);
        return toChatResult(sessionId, route, context, toolResult, toolResult.getReply(), springAiReady, plan, trace);
    }

    private void updateSessionAfterTool(
            CurrentUser currentUser,
            String sessionId,
            AgentSessionContext context,
            IntentRoute route,
            String message,
            AgentToolParameters parameters,
            AgentToolResult toolResult
    ) {
        sessionService.updateAfterTool(sessionId, context, route, message, toolResult);
        memoryService.remember(currentUser, context, route, parameters, toolResult);
        sessionService.save(sessionId, context);
    }

    private boolean hasPendingCreateDraft(AgentSessionContext context) {
        return context != null
                && context.getPendingAction() != null
                && context.getPendingAction().getPendingIntent() == AgentIntent.CREATE_TICKET
                && !context.getPendingAction().isAwaitingConfirmation();
    }

    private boolean hasPendingConfirmation(AgentSessionContext context) {
        return context != null
                && context.getPendingAction() != null
                && context.getPendingAction().isAwaitingConfirmation();
    }

    private AgentToolResult buildConfirmationResult(
            String toolName,
            IntentRoute route,
            AgentToolParameters parameters,
            String reason
    ) {
        String summary = switch (route.getIntent()) {
            case TRANSFER_TICKET -> "\u9ad8\u98ce\u9669\u64cd\u4f5c\u9700\u8981\u786e\u8ba4\u3002\n"
                    + "\u64cd\u4f5c\uff1a\u8f6c\u6d3e\u5de5\u5355\n"
                    + "\u5de5\u5355 ID\uff1a" + valueOrUnknown(parameters == null ? null : parameters.getTicketId()) + "\n"
                    + "\u76ee\u6807\u5904\u7406\u4eba ID\uff1a" + valueOrUnknown(parameters == null ? null : parameters.getAssigneeId()) + "\n"
                    + "\u539f\u56e0\uff1a" + reason + "\n"
                    + "\u8bf7\u56de\u590d\u201c\u786e\u8ba4\u6267\u884c\u201d\u7ee7\u7eed\uff0c\u6216\u56de\u590d\u201c\u53d6\u6d88\u201d\u653e\u5f03\u3002";
            default -> "\u8be5\u64cd\u4f5c\u98ce\u9669\u8f83\u9ad8\uff0c\u9700\u8981\u4e8c\u6b21\u786e\u8ba4\u540e\u624d\u80fd\u6267\u884c\u3002\u8bf7\u56de\u590d\u201c\u786e\u8ba4\u6267\u884c\u201d\u7ee7\u7eed\uff0c\u6216\u56de\u590d\u201c\u53d6\u6d88\u201d\u653e\u5f03\u3002";
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
                .pendingToolName(toolForIntent(AgentIntent.CREATE_TICKET).name())
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
        return message.contains("\u65e0\u6cd5")
                || message.contains("\u5931\u8d25")
                || message.contains("\u5f02\u5e38")
                || message.contains("\u62a5\u9519")
                || message.contains("\u95ee\u9898")
                || message.contains("\u5f71\u54cd")
                || message.toLowerCase().contains("error");
    }

    private String buildCreateClarificationReply(
            List<AgentToolParameterField> missingFields,
            AgentToolParameters draft,
            String message
    ) {
        if (missingFields.isEmpty()) {
            return "\u8bf7\u7ee7\u7eed\u8865\u5145\u521b\u5efa\u5de5\u5355\u6240\u9700\u7684\u4fe1\u606f\u3002";
        }
        if (missingFields.size() == 1) {
            AgentToolParameterField field = missingFields.get(0);
            return switch (field) {
                case TITLE -> "\u8bf7\u8865\u5145\u5de5\u5355\u6807\u9898\uff0c\u5c3d\u91cf\u7528\u4e00\u53e5\u8bdd\u8bf4\u660e\u6838\u5fc3\u95ee\u9898\u3002";
                case DESCRIPTION -> "\u8bf7\u8865\u5145\u66f4\u5b8c\u6574\u7684\u95ee\u9898\u63cf\u8ff0\uff0c\u4f8b\u5982\u73b0\u8c61\u3001\u5f71\u54cd\u8303\u56f4\u548c\u4f60\u5df2\u5c1d\u8bd5\u8fc7\u7684\u64cd\u4f5c\u3002";
                case CATEGORY -> "\u8bf7\u8865\u5145\u5de5\u5355\u5206\u7c7b\uff1aACCOUNT\u3001SYSTEM\u3001ENVIRONMENT\u3001OTHER\u3002";
                case PRIORITY -> "\u8bf7\u8865\u5145\u5de5\u5355\u4f18\u5148\u7ea7\uff1aLOW\u3001MEDIUM\u3001HIGH\u3001URGENT\u3002";
                default -> "\u8bf7\u8865\u5145" + field.getLabel() + "\u3002";
            };
        }
        StringBuilder reply = new StringBuilder("\u6211\u5148\u8bb0\u5f55\u4e86\u5f53\u524d\u5de5\u5355\u8349\u7a3f");
        if (hasText(draft.getTitle())) {
            reply.append("\uff08\u6807\u9898\uff1a").append(draft.getTitle()).append("\uff09");
        }
        reply.append("\u3002\u8fd8\u9700\u8981\u8865\u5145\uff1a");
        for (int i = 0; i < missingFields.size(); i++) {
            if (i > 0) {
                reply.append("\u3001");
            }
            reply.append(missingFields.get(i).getLabel());
        }
        reply.append("\u3002\u6d88\u606f\u5185\u5bb9\uff1a").append(message == null ? "" : message.trim());
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
        return normalized.contains("\u53d6\u6d88")
                || normalized.contains("\u4e0d\u7528\u4e86")
                || normalized.contains("\u7b97\u4e86")
                || normalized.equals("cancel");
    }

    private boolean isConfirmMessage(String message) {
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.contains("\u786e\u8ba4")
                || normalized.contains("\u540c\u610f")
                || normalized.contains("\u6267\u884c")
                || normalized.contains("confirm")
                || normalized.equals("yes");
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "\u672a\u8bc6\u522b" : String.valueOf(value);
    }

    private Object springAiToolFor(AgentIntent intent) {
        return toolForIntent(intent);
    }

    private AgentTool toolForIntent(AgentIntent intent) {
        return skillRegistry.requireByIntent(intent).tool();
    }

    private AgentTool toolForName(String toolName) {
        return skillRegistry.requireByToolName(toolName).tool();
    }

    private AgentChatResult toChatResult(
            String sessionId,
            IntentRoute route,
            AgentSessionContext context,
            AgentToolResult toolResult,
            String reply,
            boolean springAiReady,
            AgentPlan plan,
            AgentTraceContext trace
    ) {
        return AgentChatResult.builder()
                .sessionId(sessionId)
                .intent(route.getIntent().name())
                .reply(hasText(reply) ? reply : toolResult.getReply())
                .route(route)
                .context(context)
                .result(toolResult.getData())
                .springAiChatReady(springAiReady)
                .plan(plan)
                .traceId(trace == null ? null : trace.getTraceId())
                .build();
    }

    private String systemPrompt(AgentIntent intent) {
        String fallback = switch (intent) {
            case QUERY_TICKET -> "\u4f60\u662f\u5de5\u5355\u67e5\u8be2\u52a9\u624b\u3002\u672c\u8f6e\u53ea\u5141\u8bb8\u8c03\u7528 queryTicket\uff0c\u7528\u4e8e\u67e5\u8be2\u5f53\u524d\u5de5\u5355\u4e8b\u5b9e\uff0c\u4e0d\u5f97\u68c0\u7d22\u5386\u53f2\u77e5\u8bc6\u5e93\u3002";
            case CREATE_TICKET -> "\u4f60\u662f\u5de5\u5355\u521b\u5efa\u52a9\u624b\u3002\u672c\u8f6e\u53ea\u5141\u8bb8\u8c03\u7528 createTicket\u3002\u521b\u5efa\u52a8\u4f5c\u5fc5\u987b\u901a\u8fc7\u5de5\u5177\u5b8c\u6210\uff0c\u4e0d\u80fd\u7f16\u9020\u521b\u5efa\u7ed3\u679c\u3002";
            case TRANSFER_TICKET -> "\u4f60\u662f\u5de5\u5355\u8f6c\u6d3e\u52a9\u624b\u3002\u672c\u8f6e\u53ea\u5141\u8bb8\u8c03\u7528 transferTicket\u3002\u8f6c\u6d3e\u662f\u9ad8\u98ce\u9669\u5199\u64cd\u4f5c\uff0c\u5fc5\u987b\u9075\u5b88\u5de5\u5177\u8fd4\u56de\u7684\u786e\u8ba4\u6216\u5931\u8d25\u4fe1\u606f\u3002";
            case SEARCH_HISTORY -> "\u4f60\u662f\u5386\u53f2\u7ecf\u9a8c\u68c0\u7d22\u52a9\u624b\u3002\u672c\u8f6e\u53ea\u5141\u8bb8\u8c03\u7528 searchHistory\u3002\u68c0\u7d22\u7ed3\u679c\u53ea\u4f5c\u53c2\u8003\uff0c\u4e0d\u4ee3\u8868\u5f53\u524d\u5de5\u5355\u4e8b\u5b9e\u3002";
        };
        return promptTemplateService.content(promptCodeFor(intent), fallback);
    }

    private String promptCodeFor(AgentIntent intent) {
        return switch (intent) {
            case CREATE_TICKET -> "create-ticket-completion";
            case SEARCH_HISTORY -> "history-summary";
            default -> "result-explanation";
        };
    }

    private String userPrompt(String message, IntentRoute route) {
        return """
                闂佸搫鐗滈崜婵嬫偪閸℃稑绠涢煫鍥ㄦ尰缁傚牓鏌?s
                闁荤姳璀﹂崹鎶藉极闁秴鍌ㄩ柣鏃堟敱缁€鍫ユ煥?s
                闂佹椿娼块崝宥夊春濞戞ǚ妲堥柛顐ゅ枍缁辨牠鏌?s

                闁荤姴娲弨閬嶆偋閹绢喖绠叉い鏃傚亾閺嗗繘鏌熼幘顔芥暠缂佸鍏橀獮渚€顢涘☉妯煎€掗梺鍛婄懄閻楁洘瀵奸幇鏉跨鐎瑰嫭婢樺Λ姗€鏌℃担瑙勭凡闁艰崵鍠撻幏顐﹀礃椤忓懏娈㈤梺鍝勭墱閸撴繈鎮块崱娑樼闁归偊浜炴潻鏃堟煟閵娿儱顏╁ù鍏煎姍瀹曟寮搁鐔蜂壕闁稿本鐟ч悷婵嬫偡閺囨碍绁伴柣銊у枛閹粙濡搁敂钘夌处闂佸湱绮崝鎺旀閻㈠憡鍎嶉柛鏇ㄥ亗缁憋綁鏌涜箛鏃備粵闁?                """.formatted(route.getIntent().name(), route.getReason(), message);
    }

    private boolean isSpringAiChatReady() {
        return chatEnabled && chatClientProvider.getIfAvailable() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
