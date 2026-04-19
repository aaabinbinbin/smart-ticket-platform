package com.smartticket.agent.orchestration;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import org.springframework.stereotype.Component;

/**
 * 会话内简单指代消解器。
 *
 * <p>第一版只处理明确的短指代：工单指代优先映射到 activeTicketId，处理人指代优先映射到
 * activeAssigneeId。不维护实体图谱，也不尝试跨长期记忆推断。</p>
 */
@Component
public class AgentContextReferenceResolver {
    /**
     * 根据用户消息中的简单指代补齐结构化参数。
     *
     * @param message 用户本轮消息
     * @param context 当前 Redis 会话上下文
     * @param parameters 当前待执行 Tool 的参数对象
     */
    public void applyReferences(String message, AgentSessionContext context, AgentToolParameters parameters) {
        if (context == null || parameters == null || message == null || message.isBlank()) {
            return;
        }
        if (containsTicketReference(message) && context.getActiveTicketId() != null) {
            parameters.setTicketId(context.getActiveTicketId());
        }
        if (containsTransferReference(message)
                && context.getActiveTicketId() != null
                && parameters.getNumbers() != null
                && parameters.getNumbers().size() == 1) {
            parameters.setTicketId(context.getActiveTicketId());
            parameters.setAssigneeId(parameters.getNumbers().get(0));
        }
        if (containsAssigneeReference(message) && context.getActiveAssigneeId() != null) {
            parameters.setAssigneeId(context.getActiveAssigneeId());
        }
    }

    /** 判断消息中是否包含“它 / 这个 / 刚才那个工单”等工单指代。 */
    private boolean containsTicketReference(String message) {
        return message.contains("它")
                || message.contains("这个")
                || message.contains("该工单")
                || message.contains("这个工单")
                || message.contains("刚才那个工单")
                || message.contains("刚刚那个工单");
    }

    /** 判断消息中是否包含“他 / 这个处理人”等处理人指代。 */
    private boolean containsAssigneeReference(String message) {
        return message.contains("他")
                || message.contains("这个处理人")
                || message.contains("刚才那个处理人")
                || message.contains("刚刚那个处理人");
    }

    /** 判断消息中是否包含转派语义，用于处理“把它转给 3”这类单数字补参。 */
    private boolean containsTransferReference(String message) {
        return message.contains("转派")
                || message.contains("转交")
                || message.contains("转给")
                || message.contains("移交")
                || message.toLowerCase().contains("transfer");
    }
}
