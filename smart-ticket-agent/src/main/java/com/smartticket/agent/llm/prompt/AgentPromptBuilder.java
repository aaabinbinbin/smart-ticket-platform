package com.smartticket.agent.llm.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartticket.agent.llm.model.LlmFallbackToolCallPlan;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Agent Prompt 构造器。
 *
 * <p>负责把静态 Prompt 模板和动态上下文组装成 LLM 可消费的消息内容。</p>
 */
@Component
public class AgentPromptBuilder {
    /**
     * Prompt 模板注册表，提供 system prompt 文本。
     */
    private final AgentPromptTemplateRegistry templateRegistry;

    /**
     * 用于把动态上下文序列化为 JSON，降低模型理解输入结构的难度。
     */
    private final ObjectMapper objectMapper;

    public AgentPromptBuilder(AgentPromptTemplateRegistry templateRegistry, ObjectMapper objectMapper) {
        this.templateRegistry = templateRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取指定任务的 system prompt。
     */
    public String systemPrompt(AgentPromptName name) {
        return templateRegistry.get(name);
    }

    /**
     * 构造意图识别任务的 user prompt。
     *
     * <p>输入只包含用户消息和当前会话上下文，不包含任何 RAG 片段。</p>
     */
    public String intentUserPrompt(String message, AgentSessionContext context) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", safe(message));
        payload.put("sessionContext", context);
        return toJson(payload);
    }

    /**
     * 构造参数抽取任务的 user prompt。
     *
     * <p>route 会传给模型作为辅助信息，但最终是否能执行仍由 Tool 校验决定。</p>
     */
    public String parameterUserPrompt(String message, AgentSessionContext context, IntentRoute route) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", safe(message));
        payload.put("sessionContext", context);
        payload.put("intentRoute", route);
        return toJson(payload);
    }

    /**
     * 构造缺参澄清任务的 user prompt。
     *
     * <p>fallbackReply 是 Tool 生成的保底追问，LLM 失败时直接使用它。</p>
     */
    public String clarificationUserPrompt(
            String message,
            IntentRoute route,
            List<AgentToolParameterField> missingFields,
            String fallbackReply
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", safe(message));
        payload.put("intentRoute", route);
        payload.put("missingFields", missingFields);
        payload.put("fallbackReply", safe(fallbackReply));
        return toJson(payload);
    }

    /**
     * 构造工具结果总结任务的 user prompt。
     *
     * <p>模型只能基于 toolResult 总结，不允许生成工具结果中不存在的事实。</p>
     */
    public String responseSummaryUserPrompt(
            String message,
            IntentRoute route,
            AgentToolResult toolResult
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", safe(message));
        payload.put("intentRoute", route);
        payload.put("toolName", toolResult.getToolName());
        payload.put("toolStatus", toolResult.getStatus());
        payload.put("toolReply", safe(toolResult.getReply()));
        payload.put("toolResult", toolResult.getData());
        return toJson(payload);
    }

    /**
     * 构造单 Agent 工具调用计划任务的 user prompt。
     *
     * <p>availableTools 只暴露工具元数据，不暴露任何可直接执行业务的对象。</p>
     */
    public String toolCallPlanUserPrompt(
            String message,
            AgentSessionContext context,
            IntentRoute fallbackRoute,
            LlmFallbackToolCallPlan fallbackPlan,
            List<AgentTool> availableTools
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", safe(message));
        payload.put("sessionContext", context);
        payload.put("fallbackRoute", fallbackRoute);
        payload.put("fallbackPlan", fallbackPlan);
        payload.put("availableTools", availableTools.stream()
                .map(tool -> tool.metadata())
                .toList());
        return toJson(payload);
    }

    /**
     * 将 Prompt 载荷转为 JSON。序列化失败时退化为字符串，避免影响主链路。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    /**
     * 将空字符串统一处理为空文本，避免 Prompt 载荷中出现无意义的 null 文本。
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}
