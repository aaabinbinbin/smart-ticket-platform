package com.smartticket.agent.llm.service;

import com.smartticket.agent.llm.client.LlmClient;
import com.smartticket.agent.llm.client.LlmMessage;
import com.smartticket.agent.llm.model.LlmClarificationResult;
import com.smartticket.agent.llm.model.LlmFallbackToolCallPlan;
import com.smartticket.agent.llm.model.LlmIntentDecision;
import com.smartticket.agent.llm.model.LlmParameterExtractionResult;
import com.smartticket.agent.llm.model.LlmResponseSummary;
import com.smartticket.agent.llm.model.LlmToolCallPlan;
import com.smartticket.agent.llm.prompt.AgentPromptBuilder;
import com.smartticket.agent.llm.prompt.AgentPromptName;
import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * LLM Agent 能力服务。
 *
 * <p>本服务只负责理解、抽取、澄清和总结。所有业务执行仍然必须经过 Tool 和 biz 层。</p>
 */
@Service
public class LlmAgentService {
    private static final Logger log = LoggerFactory.getLogger(LlmAgentService.class);

    /**
     * LLM 意图识别的最低可接受置信度，低于该值时使用规则路由结果。
     */
    private static final double MIN_INTENT_CONFIDENCE = 0.55;

    /**
     * LLM 客户端抽象，当前实现为 OpenAI 兼容 HTTP 客户端。
     */
    private final LlmClient llmClient;

    /**
     * Prompt 构造器，负责生成 system prompt 和动态 user prompt。
     */
    private final AgentPromptBuilder promptBuilder;

    /**
     * LLM JSON 输出解析器。
     */
    private final LlmJsonParser jsonParser;

    public LlmAgentService(LlmClient llmClient, AgentPromptBuilder promptBuilder, LlmJsonParser jsonParser) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.jsonParser = jsonParser;
    }

    /**
     * 使用 LLM 尝试识别意图，失败或低置信度时回退到规则路由。
     *
     * @param message 用户原始消息
     * @param context 当前会话上下文
     * @param fallbackRoute 规则 IntentRouter 生成的兜底路由
     * @return 通过校验的 LLM 路由，或 fallbackRoute
     */
    public IntentRoute routeOrFallback(String message, AgentSessionContext context, IntentRoute fallbackRoute) {
        if (!llmClient.isAvailable()) {
            return fallbackRoute;
        }
        try {
            String content = llmClient.complete(List.of(
                    LlmMessage.system(promptBuilder.systemPrompt(AgentPromptName.INTENT_CLASSIFICATION)),
                    LlmMessage.user(promptBuilder.intentUserPrompt(message, context))
            ));
            LlmIntentDecision decision = jsonParser.parse(content, LlmIntentDecision.class);
            IntentRoute validated = validateIntent(decision);
            if (validated == null) {
                return fallbackRoute;
            }
            log.info("agent llm intent decision: decision={}", decision);
            return validated;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("agent llm intent fallback: reason={}", ex.getMessage());
            return fallbackRoute;
        }
    }

    /**
     * 使用 LLM 尝试抽取 Tool 参数，失败时回退到规则抽取结果。
     *
     * <p>LLM 输出只做增强，不直接绕过 Tool 的 requiredFields 校验。</p>
     */
    public AgentToolParameters extractParametersOrFallback(
            String message,
            AgentSessionContext context,
            IntentRoute route,
            AgentToolParameters fallbackParameters
    ) {
        if (!llmClient.isAvailable()) {
            return fallbackParameters;
        }
        try {
            String content = llmClient.complete(List.of(
                    LlmMessage.system(promptBuilder.systemPrompt(AgentPromptName.TICKET_PARAMETER_EXTRACTION)),
                    LlmMessage.user(promptBuilder.parameterUserPrompt(message, context, route))
            ));
            LlmParameterExtractionResult result = jsonParser.parse(content, LlmParameterExtractionResult.class);
            AgentToolParameters validated = validateParameters(result, fallbackParameters);
            log.info("agent llm parameter extraction: raw={}, validated={}", result, validated);
            return validated;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("agent llm parameter fallback: reason={}", ex.getMessage());
            return fallbackParameters;
        }
    }

    /**
     * 使用 LLM 生成缺参澄清问题。
     *
     * <p>如果 LLM 不可用、输出不可解析或没有生成有效问题，则使用 Tool 的原始缺参回复。</p>
     */
    public String clarifyOrFallback(
            String message,
            IntentRoute route,
            List<AgentToolParameterField> missingFields,
            String fallbackReply
    ) {
        if (!llmClient.isAvailable()) {
            return fallbackReply;
        }
        try {
            String content = llmClient.complete(List.of(
                    LlmMessage.system(promptBuilder.systemPrompt(AgentPromptName.CLARIFICATION_QUESTION)),
                    LlmMessage.user(promptBuilder.clarificationUserPrompt(message, route, missingFields, fallbackReply))
            ));
            LlmClarificationResult result = jsonParser.parse(content, LlmClarificationResult.class);
            if (hasText(result.getQuestion())) {
                return result.getQuestion().trim();
            }
            return fallbackReply;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("agent llm clarification fallback: reason={}", ex.getMessage());
            return fallbackReply;
        }
    }

    /**
     * 使用 LLM 总结 Tool 执行结果。
     *
     * <p>NEED_MORE_INFO 状态不走结果总结，而是由 clarifyOrFallback 生成追问。</p>
     */
    public String summarizeOrFallback(String message, IntentRoute route, AgentToolResult toolResult) {
        if (!llmClient.isAvailable() || toolResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            return toolResult.getReply();
        }
        try {
            String content = llmClient.complete(List.of(
                    LlmMessage.system(promptBuilder.systemPrompt(AgentPromptName.RESPONSE_SUMMARY)),
                    LlmMessage.user(promptBuilder.responseSummaryUserPrompt(message, route, toolResult))
            ));
            LlmResponseSummary summary = jsonParser.parse(content, LlmResponseSummary.class);
            if (hasText(summary.getReply())) {
                return summary.getReply().trim();
            }
            return toolResult.getReply();
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("agent llm response summary fallback: reason={}", ex.getMessage());
            return toolResult.getReply();
        }
    }

    /**
     * 使用 LLM 生成单 Agent 工具调用计划。
     *
     * <p>该方法只返回模型计划，不执行 Tool；计划合法性由 TicketAgentOrchestrator 继续校验。</p>
     */
    public Optional<LlmToolCallPlan> planToolCall(
            String message,
            AgentSessionContext context,
            IntentRoute fallbackRoute,
            LlmFallbackToolCallPlan fallbackPlan,
            List<AgentTool> availableTools
    ) {
        if (!llmClient.isAvailable()) {
            return Optional.empty();
        }
        try {
            String content = llmClient.complete(List.of(
                    LlmMessage.system(promptBuilder.systemPrompt(AgentPromptName.TOOL_CALL_PLAN)),
                    LlmMessage.user(promptBuilder.toolCallPlanUserPrompt(
                            message, context, fallbackRoute, fallbackPlan, availableTools))
            ));
            LlmToolCallPlan plan = jsonParser.parse(content, LlmToolCallPlan.class);
            log.info("agent llm tool call plan: plan={}", plan);
            return Optional.ofNullable(plan);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("agent llm tool call plan fallback: reason={}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 将 LLM 参数抽取结果与 fallback 参数合并。
     *
     * <p>供阶段九编排器复用阶段八的参数校验逻辑。</p>
     */
    public AgentToolParameters mergeParametersOrFallback(
            LlmParameterExtractionResult result,
            AgentToolParameters fallbackParameters
    ) {
        return validateParameters(result, fallbackParameters);
    }

    /**
     * 校验 LLM 意图识别结果。
     *
     * <p>这里会限制置信度范围，并过滤低置信度或缺少必要字段的结果。</p>
     */
    private IntentRoute validateIntent(LlmIntentDecision decision) {
        if (decision == null || decision.getIntent() == null || decision.getConfidence() == null) {
            return null;
        }
        double confidence = Math.max(0.0, Math.min(1.0, decision.getConfidence()));
        if (confidence < MIN_INTENT_CONFIDENCE) {
            return null;
        }
        return IntentRoute.builder()
                .intent(decision.getIntent())
                .confidence(confidence)
                .reason(hasText(decision.getReason()) ? "LLM: " + decision.getReason().trim() : "LLM 结构化识别")
                .build();
    }

    /**
     * 校验并合并 LLM 参数抽取结果。
     *
     * <p>LLM 未提供或提供非法值时，保留规则抽取结果，避免模型输出破坏可用性。</p>
     */
    private AgentToolParameters validateParameters(
            LlmParameterExtractionResult result,
            AgentToolParameters fallbackParameters
    ) {
        if (result == null) {
            return fallbackParameters;
        }
        TicketCategoryEnum category = parseCategory(result.getCategory(), fallbackParameters.getCategory());
        TicketPriorityEnum priority = parsePriority(result.getPriority(), fallbackParameters.getPriority());
        return AgentToolParameters.builder()
                .ticketId(result.getTicketId() == null ? fallbackParameters.getTicketId() : result.getTicketId())
                .assigneeId(result.getAssigneeId() == null ? fallbackParameters.getAssigneeId() : result.getAssigneeId())
                .title(hasText(result.getTitle()) ? result.getTitle().trim() : fallbackParameters.getTitle())
                .description(hasText(result.getDescription()) ? result.getDescription().trim() : fallbackParameters.getDescription())
                .category(category)
                .priority(priority)
                .numbers(validNumbers(result.getNumbers(), fallbackParameters.getNumbers()))
                .build();
    }

    /**
     * 将 LLM 返回的分类编码转换为领域枚举，非法值回退到规则抽取结果。
     */
    private TicketCategoryEnum parseCategory(String value, TicketCategoryEnum fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        try {
            return TicketCategoryEnum.fromCode(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /**
     * 将 LLM 返回的优先级编码转换为领域枚举，非法值回退到规则抽取结果。
     */
    private TicketPriorityEnum parsePriority(String value, TicketPriorityEnum fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        try {
            return TicketPriorityEnum.fromCode(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /**
     * 校验 LLM 抽取出的数字列表。为空时使用规则抽取结果。
     */
    private List<Long> validNumbers(List<Long> numbers, List<Long> fallback) {
        if (numbers == null || numbers.isEmpty()) {
            return fallback;
        }
        return new ArrayList<>(numbers);
    }

    /**
     * 本类内部使用的字符串非空判断。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
