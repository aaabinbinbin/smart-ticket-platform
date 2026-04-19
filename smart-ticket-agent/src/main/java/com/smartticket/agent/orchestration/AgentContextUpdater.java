package com.smartticket.agent.orchestration;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Agent 会话上下文更新器。
 *
 * <p>该类只维护 Agent 会话状态，不直接写工单事实数据。阶段十的 pending action
 * 可以继续在这里扩展。</p>
 */
@Component
public class AgentContextUpdater {
    /**
     * 会话中保留的最近消息数量，避免上下文无限增长。
     */
    private static final int MAX_RECENT_MESSAGES = 10;

    /**
     * 根据 Tool 观察结果更新会话上下文。
     */
    public void apply(
            AgentSessionContext context,
            IntentRoute route,
            String message,
            AgentToolResult toolResult
    ) {
        context.setLastIntent(route.getIntent().name());
        if (toolResult.getActiveTicketId() != null) {
            context.setActiveTicketId(toolResult.getActiveTicketId());
        }
        if (toolResult.getActiveAssigneeId() != null) {
            context.setActiveAssigneeId(toolResult.getActiveAssigneeId());
        }
        List<String> messages = context.getRecentMessages() == null
                ? new ArrayList<>()
                : new ArrayList<>(context.getRecentMessages());
        messages.add(route.getIntent().name() + ": " + message);
        while (messages.size() > MAX_RECENT_MESSAGES) {
            messages.remove(0);
        }
        context.setRecentMessages(messages);
    }
}
