package com.smartticket.agent.service;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.agent.router.LlmIntentClassifier;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 意图路由器：优先使用 LLM 做意图分类，不可用时回退到关键词匹配。
 *
 * <p>在 ReAct 模式下，路由结果作为系统上下文提供给 LLM 参考（非约束），
 * LLM 可以自主选择不同于路由结果的工具组合。
 * 在确定性回退路径中，路由仍直接决定执行的工具。
 * LLM 不可用时由可解释的关键词规则兜底，确保 P0 闭环。</p>
 */
@Service
public class IntentRouter {
    private static final Logger log = LoggerFactory.getLogger(IntentRouter.class);
    private static final List<String> HISTORY_KEYWORDS = List.of(
            "历史", "历史案例", "类似案例", "经验", "方案", "记录",
            "类似", "相似", "知识库", "解决方案", "怎么处理",
            "history", "historical", "previous", "similar", "knowledge base", "how was it handled"
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
            "查询", "查看", "详情", "状态", "进度", "列表", "工单", "ticket", "status", "detail",
            "查"
    );

    private final LlmIntentClassifier llmClassifier;

    public IntentRouter(LlmIntentClassifier llmClassifier) {
        this.llmClassifier = llmClassifier;
    }

    /**
     * 根据用户输入和会话上下文推断意图路由结果。
     */
    public IntentRoute route(String message, AgentSessionContext context) {
        String trimmed = normalize(message);
        if (!hasText(trimmed)) {
            return route(AgentIntent.QUERY_TICKET, 0.25, "用户消息为空");
        }

        // 优先使用 LLM 意图分类
        IntentRoute llmRoute = llmClassifier.classify(trimmed);
        if (llmRoute != null) {
            log.info("LLM 意图路由：intent={}, confidence={}, reason={}",
                    llmRoute.getIntent(), llmRoute.getConfidence(), llmRoute.getReason());
            return llmRoute;
        }

        // LLM 不可用时回退到关键词匹配
        return keywordRoute(trimmed, context);
    }

    /**
     * 关键词匹配路由（原始 fallback 逻辑）。
     */
    private IntentRoute keywordRoute(String message, AgentSessionContext context) {
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

    /**
     * 构造路由结果对象。
     */
    private IntentRoute route(AgentIntent intent, double confidence, String reason) {
        return IntentRoute.builder()
                .intent(intent)
                .confidence(confidence)
                .reason(reason)
                .build();
    }

    /**
     * 判断消息中是否命中任一关键字。
     */
    private boolean containsAny(String message, List<String> keywords) {
        return keywords.stream().anyMatch(message::contains);
    }

    /**
     * 判断消息中是否包含当前工单的指代表达。
     */
    private boolean containsTicketReference(String message) {
        return message.contains("它")
                || message.contains("这个")
                || message.contains("该工单")
                || message.contains("这个工单")
                || message.contains("刚才那个工单")
                || message.contains("刚刚那个工单");
    }

    /**
     * 统计命中的意图数量。
     */
    private int countMatches(boolean... matches) {
        int count = 0;
        for (boolean match : matches) {
            if (match) {
                count++;
            }
        }
        return count;
    }

    /**
     * 规范化处理。
     */
    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断字符串不为空。
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
