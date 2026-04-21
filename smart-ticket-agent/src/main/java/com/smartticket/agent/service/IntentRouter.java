package com.smartticket.agent.service;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 当前版本的规则意图路由器。
 *
 * <p>优先使用可解释规则完成基础路由，并在置信度过低时交由上层先做澄清，
 * 避免默认把模糊请求硬路由到 QUERY_TICKET。</p>
 */
@Service
public class IntentRouter {
    private static final List<String> HISTORY_KEYWORDS = List.of(
            "历史", "历史案例", "类似案例", "经验", "方案", "记录", "history", "previous"
    );
    private static final List<String> CONTEXT_KEYWORDS = List.of(
            "刚才", "之前", "上次", "前面", "这个", "它"
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
        boolean transfer = containsAny(normalized, TRANSFER_KEYWORDS);
        boolean create = containsAny(normalized, CREATE_KEYWORDS);
        boolean history = containsAny(normalized, HISTORY_KEYWORDS);
        boolean query = containsAny(normalized, QUERY_KEYWORDS);
        boolean ticketReference = containsTicketReference(normalized);
        int matchedIntents = countMatches(transfer, create, history, query);

        if (matchedIntents > 1) {
            if (transfer) {
                return route(AgentIntent.TRANSFER_TICKET, 0.72, "混合表达中优先命中转派规则");
            }
            if (create) {
                return route(AgentIntent.CREATE_TICKET, 0.68, "混合表达中优先命中创建规则");
            }
            if (history) {
                return route(AgentIntent.SEARCH_HISTORY, 0.66, "混合表达中优先命中历史检索规则");
            }
        }
        if (transfer) {
            return route(AgentIntent.TRANSFER_TICKET, 0.90, "命中转派关键词");
        }
        if (create) {
            return route(AgentIntent.CREATE_TICKET, 0.88, "命中创建关键词");
        }
        if (ticketReference && query) {
            return route(AgentIntent.QUERY_TICKET, 0.88, "命中当前会话工单指代查询");
        }
        if (history) {
            return route(AgentIntent.SEARCH_HISTORY, 0.92, "命中历史查询关键词");
        }
        if (query) {
            return route(AgentIntent.QUERY_TICKET, 0.82, "命中查询关键词");
        }
        if (context != null && context.getActiveTicketId() != null && containsAny(normalized, CONTEXT_KEYWORDS)) {
            return route(AgentIntent.QUERY_TICKET, 0.58, "命中会话上下文指代，倾向查询当前工单");
        }
        if (context != null && context.getActiveTicketId() != null) {
            return route(AgentIntent.QUERY_TICKET, 0.46, "上下文中存在当前工单，但用户表达仍然偏模糊");
        }
        return route(AgentIntent.QUERY_TICKET, 0.25, "未命中明确意图规则，需要先澄清用户目标");
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

    private boolean containsTicketReference(String message) {
        return message.contains("它")
                || message.contains("这个")
                || message.contains("该工单")
                || message.contains("这个工单")
                || message.contains("刚才那个工单")
                || message.contains("刚刚那个工单");
    }

    private int countMatches(boolean... matches) {
        int count = 0;
        for (boolean match : matches) {
            if (match) {
                count++;
            }
        }
        return count;
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }
}
