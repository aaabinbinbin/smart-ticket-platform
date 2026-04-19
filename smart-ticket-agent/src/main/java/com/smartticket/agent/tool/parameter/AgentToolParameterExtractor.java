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
 */
@Component
public class AgentToolParameterExtractor {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    public AgentToolParameters extract(String message, AgentSessionContext context) {
        List<Long> numbers = numbers(message);
        Long contextTicketId = context == null ? null : context.getActiveTicketId();
        Long ticketId = numbers.isEmpty() ? contextTicketId : numbers.get(0);
        Long assigneeId = numbers.size() >= 2 ? numbers.get(1) : null;
        return AgentToolParameters.builder()
                .ticketId(ticketId)
                .assigneeId(assigneeId)
                .title(resolveTitle(message))
                .description(message)
                .category(resolveCategory(message))
                .priority(resolvePriority(message))
                .numbers(numbers)
                .build();
    }

    private List<Long> numbers(String message) {
        Matcher matcher = NUMBER_PATTERN.matcher(message == null ? "" : message);
        return matcher.results()
                .map(result -> Long.parseLong(result.group()))
                .toList();
    }

    private String resolveTitle(String message) {
        String title = message == null ? "" : message.trim();
        title = title.replaceFirst("^(创建|新建|提交|发起|报修|开单)\\s*", "");
        if (title.isBlank()) {
            return "Agent 创建工单";
        }
        return title.length() <= 80 ? title : title.substring(0, 80);
    }

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
        return TicketCategoryEnum.OTHER;
    }

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
        return TicketPriorityEnum.MEDIUM;
    }
}
