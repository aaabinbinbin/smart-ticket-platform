package com.smartticket.agent.tool.parameter;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 第一版规则参数提取器。
 *
 * <p>只做确定性、可解释的浅层提取，缺失字段交给 Tool 和多轮澄清处理。</p>
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
                .description(resolveDescription(message))
                .type(resolveType(message))
                .category(resolveCategory(message))
                .priority(resolvePriority(message))
                .summaryRequested(resolveSummaryRequested(message))
                .summaryView(resolveSummaryView(message))
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
            return null;
        }
        return title.length() <= 80 ? title : title.substring(0, 80);
    }

    private String resolveDescription(String message) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }
        return message.trim();
    }

    private TicketTypeEnum resolveType(String message) {
        String text = message == null ? "" : message;
        String lower = text.toLowerCase();
        if (text.contains("权限申请") || text.contains("开权限") || text.contains("申请权限") || lower.contains("access")) {
            return TicketTypeEnum.ACCESS_REQUEST;
        }
        if (text.contains("环境申请") || text.contains("申请环境") || text.contains("开环境") || lower.contains("environment request")) {
            return TicketTypeEnum.ENVIRONMENT_REQUEST;
        }
        if (text.contains("咨询") || text.contains("请教") || text.contains("怎么") || lower.contains("consult")) {
            return TicketTypeEnum.CONSULTATION;
        }
        if (text.contains("变更") || text.contains("发布") || text.contains("回滚") || lower.contains("change")) {
            return TicketTypeEnum.CHANGE_REQUEST;
        }
        if (text.contains("故障") || text.contains("异常") || text.contains("报错") || text.contains("无法") || lower.contains("incident")) {
            return TicketTypeEnum.INCIDENT;
        }
        return null;
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
        if (text.contains("其他") || lower.contains("other")) {
            return TicketCategoryEnum.OTHER;
        }
        return null;
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
        if (text.contains("中") || text.contains("medium")) {
            return TicketPriorityEnum.MEDIUM;
        }
        return null;
    }

    private Boolean resolveSummaryRequested(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("摘要")
                || text.contains("总结")
                || text.contains("概览")
                || text.contains("汇总")
                || text.contains("风险");
    }

    private TicketSummaryViewEnum resolveSummaryView(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("管理员") || text.contains("管理视角") || text.contains("风险")) {
            return TicketSummaryViewEnum.ADMIN;
        }
        if (text.contains("处理人") || text.contains("工程师") || text.contains("执行人")) {
            return TicketSummaryViewEnum.ASSIGNEE;
        }
        if (text.contains("提单人") || text.contains("申请人") || text.contains("发起人") || text.contains("提交人")) {
            return TicketSummaryViewEnum.SUBMITTER;
        }
        return null;
    }
}
