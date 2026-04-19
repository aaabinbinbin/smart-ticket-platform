package com.smartticket.agent.tool.ticket;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.support.AgentToolResults;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询当前会话历史 Tool。
 */
@Component
public class SearchHistoryTool implements AgentTool {
    private static final String NAME = "searchHistory";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean support(AgentIntent intent) {
        return intent == AgentIntent.SEARCH_HISTORY;
    }

    @Override
    public AgentToolMetadata metadata() {
        return AgentToolMetadata.builder()
                .name(NAME)
                .description("查询当前 Agent 会话内的近期消息历史")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .readOnly(true)
                .requireConfirmation(false)
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        AgentSessionContext context = request.getContext();
        List<String> messages = context == null || context.getRecentMessages() == null
                ? List.of()
                : context.getRecentMessages();
        return AgentToolResults.success(
                NAME,
                messages.isEmpty() ? "当前会话暂无历史消息。" : "已查询当前会话历史。",
                messages
        );
    }
}
