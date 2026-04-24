package com.smartticket.agent.orchestration;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.tool.core.AgentToolResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 智能体会话上下文更新器。
 *
 * <p>该类只维护通用会话指针和最近消息，不直接写工单事实数据，也不承担长期记忆。
 * 当前 Spring AI 主链路和确定性兜底链路都会在 Tool 执行后调用它。</p>
 */
@Component
public class AgentContextUpdater {
    /** 会话中保留的最近消息数量，避免上下文无限增长。 */
    private static final int MAX_RECENT_MESSAGES = 10;

    /**
     * 根据 Tool 观察结果更新会话上下文。
     *
     * @param context 当前 Redis 会话上下文
     * @param route 本轮 Agent 路由结果
     * @param message 用户原始消息
     * @param toolResult Tool 执行或澄清结果
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
