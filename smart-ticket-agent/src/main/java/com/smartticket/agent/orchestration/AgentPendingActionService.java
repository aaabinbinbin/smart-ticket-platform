package com.smartticket.agent.orchestration;

import com.smartticket.agent.execution.AgentExecutionDecision;
import com.smartticket.agent.execution.AgentExecutionGuard;
import com.smartticket.agent.llm.service.LlmAgentService;
import com.smartticket.agent.model.AgentPendingAction;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterExtractor;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.biz.model.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * pending action 处理服务。
 *
 * <p>该服务负责在多轮对话中恢复上一轮未完成动作：抽取本轮补充参数、合并到 pending 参数、
 * 重新经过计划校验和 Tool 执行器。它只管理 Agent 会话状态，不直接调用业务 service。</p>
 */
@Component
public class AgentPendingActionService {
    private static final Logger log = LoggerFactory.getLogger(AgentPendingActionService.class);

    /** 规则参数抽取器，用于在 pending 补参时先得到确定性兜底结果。 */
    private final AgentToolParameterExtractor parameterExtractor;

    /** LLM 参数抽取服务，只负责理解用户补充内容，不执行 Tool。 */
    private final LlmAgentService llmAgentService;

    /** Agent 执行边界守卫，确保 pending 恢复后仍然满足统一执行策略。 */
    private final AgentExecutionGuard executionGuard;

    /** Tool 执行器，负责确认判断、必填参数校验以及真正调用 Tool。 */
    private final AgentToolExecutor toolExecutor;

    /** 会话指代消解器，用于把“它”“刚才那个工单”等映射到当前上下文实体。 */
    private final AgentContextReferenceResolver referenceResolver;

    public AgentPendingActionService(
            AgentToolParameterExtractor parameterExtractor,
            LlmAgentService llmAgentService,
            AgentExecutionGuard executionGuard,
            AgentToolExecutor toolExecutor,
            AgentContextReferenceResolver referenceResolver
    ) {
        this.parameterExtractor = parameterExtractor;
        this.llmAgentService = llmAgentService;
        this.executionGuard = executionGuard;
        this.toolExecutor = toolExecutor;
        this.referenceResolver = referenceResolver;
    }

    /** 判断当前会话是否存在等待补充或确认的动作。 */
    public boolean hasPendingAction(AgentSessionContext context) {
        return context != null
                && context.getPendingAction() != null
                && context.getPendingAction().getPendingIntent() != null
                && hasText(context.getPendingAction().getPendingToolName());
    }

    /**
     * 恢复并尝试继续执行 pending action。
     *
     * <p>返回结果仍然可能是 NEED_MORE_INFO，此时调用方需要继续保存 pending action。</p>
     */
    public AgentPendingActionResumeResult resume(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context
    ) {
        AgentPendingAction pendingAction = context.getPendingAction();
        IntentRoute route = routeFromPending(pendingAction);
        AgentToolParameters mergedParameters = mergeSupplementalParameters(
                pendingAction.getPendingParameters(),
                extractSupplementalParameters(message, context, route),
                pendingAction.getAwaitingFields(),
                message
        );
        referenceResolver.applyReferences(message, context, mergedParameters);

        ToolCallPlan plan = ToolCallPlan.builder()
                .intent(pendingAction.getPendingIntent())
                .toolName(pendingAction.getPendingToolName())
                .parameters(mergedParameters)
                .llmGenerated(false)
                .reason("继续执行 pending action")
                .build();
        AgentExecutionDecision decision = executionGuard.check(currentUser, message, context, route, plan);
        if (decision.isRejected()) {
            log.warn("agent pending action rejected: reason={}, plan={}", decision.getReason(), plan);
            AgentToolResult failedResult = decision.toToolResult(plan.getToolName());
            failedResult.setReply("当前待继续动作已失效，请重新发起请求。");
            return AgentPendingActionResumeResult.builder()
                    .route(route)
                    .plan(plan)
                    .toolResult(failedResult)
                    .build();
        }
        log.info("agent pending action resumed: plan={}, decision={}", plan, decision);
        AgentToolResult toolResult = toolExecutor.execute(currentUser, message, context, route, plan, decision);
        return AgentPendingActionResumeResult.builder()
                .route(route)
                .plan(plan)
                .toolResult(toolResult)
                .build();
    }

    /**
     * 根据 Tool 执行结果刷新 pending action。
     *
     * <p>NEED_MORE_INFO 表示动作未完成，需要继续等待用户补充；SUCCESS / FAILED 都会清理 pending action。</p>
     */
    public void refreshPendingAction(
            AgentSessionContext context,
            IntentRoute route,
            ToolCallPlan plan,
            AgentToolResult toolResult
    ) {
        if (context == null) {
            return;
        }
        if (toolResult.getStatus() != AgentToolStatus.NEED_MORE_INFO) {
            context.setPendingAction(null);
            return;
        }
        context.setPendingAction(AgentPendingAction.builder()
                .pendingIntent(route.getIntent())
                .pendingToolName(plan.getToolName())
                .pendingParameters(plan.getParameters())
                .awaitingFields(missingFields(toolResult.getData()))
                .lastToolResult(toolResult)
                .build());
    }

    /** 从 pending action 构造本轮兼容响应需要的路由对象。 */
    public IntentRoute routeFromPending(AgentPendingAction pendingAction) {
        return IntentRoute.builder()
                .intent(pendingAction.getPendingIntent())
                .confidence(1.0)
                .reason("继续 pending action")
                .build();
    }

    /** 从 pending action 构造本轮待恢复的 Tool 调用计划。 */
    public ToolCallPlan planFromPending(AgentPendingAction pendingAction) {
        return ToolCallPlan.builder()
                .intent(pendingAction.getPendingIntent())
                .toolName(pendingAction.getPendingToolName())
                .parameters(pendingAction.getPendingParameters())
                .llmGenerated(false)
                .reason("继续 pending action")
                .build();
    }

    /** 抽取用户本轮补充参数，LLM 不可用时回退到规则抽取结果。 */
    private AgentToolParameters extractSupplementalParameters(
            String message,
            AgentSessionContext context,
            IntentRoute route
    ) {
        AgentToolParameters fallbackParameters = parameterExtractor.extract(message, context);
        return llmAgentService.extractParametersOrFallback(message, context, route, fallbackParameters);
    }

    /**
     * 合并 pending 参数和本轮补充参数。
     *
     * <p>补参合并只填充原本为空的字段，避免用户补充“优先级是高”时把已有标题、描述误覆盖为本轮短句。</p>
     */
    private AgentToolParameters mergeSupplementalParameters(
            AgentToolParameters pendingParameters,
            AgentToolParameters supplementalParameters,
            List<AgentToolParameterField> awaitingFields,
            String message
    ) {
        AgentToolParameters merged = copyOf(pendingParameters);
        if (supplementalParameters == null) {
            return merged;
        }
        List<AgentToolParameterField> awaiting = awaitingFields == null ? List.of() : awaitingFields;
        if (merged.getTicketId() == null && supplementalParameters.getTicketId() != null
                && (awaiting.contains(AgentToolParameterField.TICKET_ID) || hasNumbers(supplementalParameters))) {
            merged.setTicketId(supplementalParameters.getTicketId());
        }
        if (merged.getAssigneeId() == null && supplementalParameters.getAssigneeId() != null
                && (awaiting.contains(AgentToolParameterField.ASSIGNEE_ID) || hasNumbers(supplementalParameters))) {
            merged.setAssigneeId(supplementalParameters.getAssigneeId());
        }
        if (merged.getAssigneeId() == null
                && awaiting.contains(AgentToolParameterField.ASSIGNEE_ID)
                && supplementalParameters.getNumbers() != null
                && supplementalParameters.getNumbers().size() == 1) {
            merged.setAssigneeId(supplementalParameters.getNumbers().get(0));
        }
        if (!hasText(merged.getTitle()) && hasText(supplementalParameters.getTitle()) && isExplicitTitle(message)) {
            merged.setTitle(supplementalParameters.getTitle());
        }
        if (!hasText(merged.getDescription()) && hasText(supplementalParameters.getDescription())
                && isExplicitDescription(message)) {
            merged.setDescription(supplementalParameters.getDescription());
        }
        if (merged.getCategory() == null && supplementalParameters.getCategory() != null && isExplicitCategory(message)) {
            merged.setCategory(supplementalParameters.getCategory());
        }
        if (merged.getPriority() == null && supplementalParameters.getPriority() != null && isExplicitPriority(message)) {
            merged.setPriority(supplementalParameters.getPriority());
        }
        if ((merged.getNumbers() == null || merged.getNumbers().isEmpty()) && supplementalParameters.getNumbers() != null) {
            merged.setNumbers(new ArrayList<>(supplementalParameters.getNumbers()));
        }
        return merged;
    }

    /** 复制参数对象，避免直接修改 Redis 上下文里已有的 pending 参数引用。 */
    private AgentToolParameters copyOf(AgentToolParameters source) {
        if (source == null) {
            return AgentToolParameters.builder().build();
        }
        return AgentToolParameters.builder()
                .ticketId(source.getTicketId())
                .assigneeId(source.getAssigneeId())
                .title(source.getTitle())
                .description(source.getDescription())
                .category(source.getCategory())
                .priority(source.getPriority())
                .numbers(source.getNumbers() == null ? new ArrayList<>() : new ArrayList<>(source.getNumbers()))
                .build();
    }

    /** 从 ToolResult.data 中提取缺失字段列表。 */
    private List<AgentToolParameterField> missingFields(Object data) {
        if (!(data instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(AgentToolParameterField.class::isInstance)
                .map(AgentToolParameterField.class::cast)
                .toList();
    }

    /** 判断补充参数中是否有明确数字，避免默认上下文 ID 被误认为用户本轮显式补参。 */
    private boolean hasNumbers(AgentToolParameters parameters) {
        return parameters.getNumbers() != null && !parameters.getNumbers().isEmpty();
    }

    /** 判断用户是否显式补充标题。 */
    private boolean isExplicitTitle(String message) {
        return hasText(message) && (message.contains("标题") || message.contains("主题"));
    }

    /** 判断用户是否显式补充描述。 */
    private boolean isExplicitDescription(String message) {
        return hasText(message)
                && (message.contains("描述") || message.contains("内容") || message.contains("问题是"));
    }

    /** 判断用户是否显式补充工单分类。 */
    private boolean isExplicitCategory(String message) {
        return hasText(message)
                && (message.contains("分类")
                || message.contains("类别")
                || message.contains("账号")
                || message.contains("权限")
                || message.contains("系统")
                || message.contains("功能")
                || message.contains("环境")
                || message.contains("配置"));
    }

    /** 判断用户是否显式补充优先级。 */
    private boolean isExplicitPriority(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("优先级")
                || text.contains("紧急")
                || text.contains("高")
                || text.contains("低")
                || text.contains("urgent")
                || text.contains("high")
                || text.contains("low");
    }

    /** 字符串非空判断。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
