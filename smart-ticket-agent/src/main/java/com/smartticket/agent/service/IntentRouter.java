package com.smartticket.agent.service;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 第一版 Agent 入口使用的规则意图路由器。
 */
@Service
public class IntentRouter {
    private static final List<String> HISTORY_KEYWORDS = List.of(
            "历史", "记录", "刚才", "之前", "上次", "前面", "history", "previous"
    );
    private static final List<String> TRANSFER_KEYWORDS = List.of(
            "转派", "转交", "转给", "移交", "transfer", "handover"
    );
    private static final List<String> CREATE_KEYWORDS = List.of(
            "创建", "新建", "提交", "发起", "报修", "开单", "create", "new ticket"
    );
    private static final List<String> QUERY_KEYWORDS = List.of(
            "查询", "查看", "详情", "状态", "进度", "列表", "工单", "ticket", "status", "detail"
    );

    public IntentRoute route(String message, AgentSessionContext context) {
        String normalized = normalize(message);
        if (containsAny(normalized, HISTORY_KEYWORDS)) {
            return route(AgentIntent.SEARCH_HISTORY, 0.92, "命中历史查询关键词");
        }
        if (containsAny(normalized, TRANSFER_KEYWORDS)) {
            return route(AgentIntent.TRANSFER_TICKET, 0.90, "命中转派关键词");
        }
        if (containsAny(normalized, CREATE_KEYWORDS)) {
            return route(AgentIntent.CREATE_TICKET, 0.88, "命中创建关键词");
        }
        if (containsAny(normalized, QUERY_KEYWORDS)) {
            return route(AgentIntent.QUERY_TICKET, 0.82, "命中查询关键词");
        }
        if (context != null && context.getActiveTicketId() != null) {
            return route(AgentIntent.QUERY_TICKET, 0.55, "兜底查询当前会话工单");
        }
        return route(AgentIntent.QUERY_TICKET, 0.40, "兜底进入工单查询");
    }

    private IntentRoute route(AgentIntent intent, double confidence, String reason) {
        return IntentRoute.builder()
                .intent(intent)
                .confidence(confidence)
                .reason(reason)
                .build();
    }

    private boolean containsAny(String message, List<String> keywords) {
        return keywords.stream().anyMatch(message::contains);
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }
}
