package com.smartticket.agent.orchestration;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import org.springframework.stereotype.Component;

/**
 * 会话内最小指代消解器。
 *
 * <p>第一版只处理短上下文里的明确指代：工单指代映射到 activeTicketId，
 * 处理人指代映射到 activeAssigneeId。不维护长期记忆，也不做复杂实体图谱推断。</p>
 */
@Component
public class AgentContextReferenceResolver {

    /**
     * 根据用户消息中的简单指代补齐 Tool 参数。
     *
     * @param message 用户本轮消息
     * @param context 当前 Redis 会话上下文
     * @param parameters 当前待执行 Tool 的参数对象
     */
    public void applyReferences(String message, AgentSessionContext context, AgentToolParameters parameters) {
        if (context == null || parameters == null || !hasText(message)) {
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

    /**
     * 判断消息中是否包含工单或问题指代。
     */
    private boolean containsTicketReference(String message) {
        return message.contains("它")
                || message.contains("这个")
                || message.contains("这个问题")
                || message.contains("该问题")
                || message.contains("这个工单")
                || message.contains("该工单")
                || message.contains("刚才那个工单")
                || message.contains("刚刚那个工单");
    }

    /**
     * 判断消息中是否包含处理人指代。
     */
    private boolean containsAssigneeReference(String message) {
        return message.contains("他")
                || message.contains("她")
                || message.contains("这个处理人")
                || message.contains("刚才那个处理人")
                || message.contains("刚刚那个处理人");
    }

    /**
     * 判断消息中是否包含转派语义。
     */
    private boolean containsTransferReference(String message) {
        String normalized = message.toLowerCase();
        return message.contains("转派")
                || message.contains("转交")
                || message.contains("转给")
                || message.contains("移交")
                || normalized.contains("transfer");
    }

    /**
     * 判断字符串是否有有效内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
