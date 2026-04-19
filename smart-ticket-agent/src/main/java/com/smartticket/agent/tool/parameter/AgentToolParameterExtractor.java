package com.smartticket.agent.tool.parameter;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 第一版规则参数抽取器。
 *
 * <p>该抽取器只做确定性、可解释的浅层抽取。没有明确表达的字段保持为空，让 Tool requiredFields
 * 校验和 pending action 负责后续追问，避免用默认值掩盖真实缺参。</p>
 */
@Component
public class AgentToolParameterExtractor {
    /** 从用户消息中抽取纯数字，用作工单 ID 或处理人 ID 的候选值。 */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    /**
     * 从用户消息和当前会话上下文中抽取 Tool 参数。
     *
     * <p>当消息没有数字时，会优先使用 activeTicketId 作为工单上下文指针；是否真的允许执行仍由 Tool
     * requiredFields 和 biz 层权限校验决定。</p>
     */
    public AgentToolParameters extract(String message, AgentSessionContext context) {
        List<Long> numbers = numbers(message);
        Long contextTicketId = context == null ? null : context.getActiveTicketId();
        Long ticketId = numbers.isEmpty() ? contextTicketId : numbers.get(0);
        Long assigneeId = numbers.size() >= 2 ? numbers.get(1) : null;
        return AgentToolParameters.builder()
                .ticketId(ticketId)
                .assigneeId(assigneeId)
                .title(resolveTitle(message))
                .description(resolveDescription(message))
                .category(resolveCategory(message))
                .priority(resolvePriority(message))
                .numbers(numbers)
                .build();
    }

    /** 抽取消息中的所有数字。 */
    private List<Long> numbers(String message) {
        Matcher matcher = NUMBER_PATTERN.matcher(message == null ? "" : message);
        return matcher.results()
                .map(result -> Long.parseLong(result.group()))
                .toList();
    }

    /** 尝试抽取工单标题；没有有效内容时返回空，交给缺参追问处理。 */
    private String resolveTitle(String message) {
        String title = message == null ? "" : message.trim();
        title = title.replaceFirst("^(创建|新建|提交|发起|报修|开单)\\s*", "");
        if (title.isBlank()) {
            return null;
        }
        return title.length() <= 80 ? title : title.substring(0, 80);
    }

    /** 当前第一版直接把原始消息作为描述；空消息返回空。 */
    private String resolveDescription(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        return message.trim();
    }

    /** 根据明确关键词抽取工单分类；无法判断时返回空。 */
    private TicketCategoryEnum resolveCategory(String message) {
        String text = message == null ? "" : message;
        String lower = text.toLowerCase();
        if (text.contains("账号") || text.contains("权限") || lower.contains("account")) {
            return TicketCategoryEnum.ACCOUNT;
        }
        if (text.contains("环境") || text.contains("配置") || lower.contains("environment")) {
            return TicketCategoryEnum.ENVIRONMENT;
        }
        if (text.contains("系统") || text.contains("功能") || lower.contains("system")) {
            return TicketCategoryEnum.SYSTEM;
        }
        if (text.contains("其他") || lower.contains("other")) {
            return TicketCategoryEnum.OTHER;
        }
        return null;
    }

    /** 根据明确关键词抽取优先级；无法判断时返回空。 */
    private TicketPriorityEnum resolvePriority(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("紧急") || text.contains("urgent")) {
            return TicketPriorityEnum.URGENT;
        }
        if (text.contains("高") || text.contains("high")) {
            return TicketPriorityEnum.HIGH;
        }
        if (text.contains("低") || text.contains("low")) {
            return TicketPriorityEnum.LOW;
        }
        if (text.contains("中") || text.contains("medium")) {
            return TicketPriorityEnum.MEDIUM;
        }
        return null;
    }
}
