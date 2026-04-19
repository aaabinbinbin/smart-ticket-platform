package com.smartticket.agent.orchestration;

import com.smartticket.agent.llm.service.LlmAgentService;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Agent 回复生成器。
 *
 * <p>根据 Tool 观察结果决定走缺参澄清还是结果总结；LLM 失败时保留 Tool 原始回复。</p>
 */
@Component
public class AgentResponseComposer {
    /**
     * LLM 能力服务。
     */
    private final LlmAgentService llmAgentService;

    public AgentResponseComposer(LlmAgentService llmAgentService) {
        this.llmAgentService = llmAgentService;
    }

    /**
     * 优化 ToolResult 中的回复文本。
     */
    public void refineReply(String message, IntentRoute route, AgentToolResult toolResult) {
        if (toolResult.getStatus() == AgentToolStatus.NEED_MORE_INFO) {
            toolResult.setReply(llmAgentService.clarifyOrFallback(
                    message,
                    route,
                    missingFields(toolResult.getData()),
                    toolResult.getReply()
            ));
            return;
        }
        toolResult.setReply(llmAgentService.summarizeOrFallback(message, route, toolResult));
    }

    /**
     * 从 ToolResult.data 中提取缺失字段列表。
     */
    private List<AgentToolParameterField> missingFields(Object data) {
        if (!(data instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(AgentToolParameterField.class::isInstance)
                .map(AgentToolParameterField.class::cast)
                .toList();
    }
}
