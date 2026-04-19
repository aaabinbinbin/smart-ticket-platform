package com.smartticket.agent.service;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.model.AgentToolResult;
import com.smartticket.agent.model.IntentRoute;
import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.TicketService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 第一版路由器使用的简单工单能力集合。
 */
@Service
public class TicketAgentCapabilityService {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final TicketService ticketService;

    public TicketAgentCapabilityService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public AgentToolResult invoke(
            CurrentUser currentUser,
            String message,
            AgentSessionContext context,
            IntentRoute route
    ) {
        AgentIntent intent = route.getIntent();
        if (intent == AgentIntent.CREATE_TICKET) {
            return createTicket(currentUser, message);
        }
        if (intent == AgentIntent.TRANSFER_TICKET) {
            return transferTicket(currentUser, message, context);
        }
        if (intent == AgentIntent.SEARCH_HISTORY) {
            return searchHistory(context);
        }
        return queryTicket(currentUser, message, context);
    }

    private AgentToolResult queryTicket(CurrentUser currentUser, String message, AgentSessionContext context) {
        Long ticketId = firstNumber(message);
        if (ticketId == null && context != null) {
            ticketId = context.getActiveTicketId();
        }
        if (ticketId != null) {
            TicketDetailDTO detail = ticketService.getDetail(currentUser, ticketId);
            return AgentToolResult.builder()
                    .invoked(true)
                    .reply("已查询工单详情。")
                    .data(detail)
                    .activeTicketId(ticketId)
                    .build();
        }

        PageResult<Ticket> page = ticketService.pageTickets(currentUser, TicketPageQueryDTO.builder()
                .pageNo(1)
                .pageSize(5)
                .build());
        return AgentToolResult.builder()
                .invoked(true)
                .reply("已查询最近可见工单列表。")
                .data(page)
                .build();
    }

    private AgentToolResult createTicket(CurrentUser currentUser, String message) {
        Ticket ticket = ticketService.createTicket(currentUser, TicketCreateCommandDTO.builder()
                .title(resolveTitle(message))
                .description(message)
                .category(resolveCategory(message))
                .priority(resolvePriority(message))
                .build());
        return AgentToolResult.builder()
                .invoked(true)
                .reply("已创建工单。")
                .data(ticket)
                .activeTicketId(ticket.getId())
                .build();
    }

    private AgentToolResult transferTicket(CurrentUser currentUser, String message, AgentSessionContext context) {
        List<Long> numbers = numbers(message);
        Long contextTicketId = context == null ? null : context.getActiveTicketId();
        Long ticketId;
        Long assigneeId;
        if (numbers.size() >= 2) {
            ticketId = numbers.get(0);
            assigneeId = numbers.get(1);
        } else if (numbers.size() == 1 && contextTicketId != null) {
            ticketId = contextTicketId;
            assigneeId = numbers.get(0);
        } else {
            ticketId = numbers.isEmpty() ? contextTicketId : numbers.get(0);
            assigneeId = null;
        }
        if (assigneeId == null) {
            return AgentToolResult.builder()
                    .invoked(false)
                    .reply("请补充目标处理人 ID，例如：将工单 12 转派给 3。")
                    .data("missing assigneeId")
                    .activeTicketId(ticketId)
                    .build();
        }
        if (ticketId == null) {
            return AgentToolResult.builder()
                    .invoked(false)
                    .reply("请补充工单 ID，例如：将工单 12 转派给 3。")
                    .data("missing ticketId")
                    .activeAssigneeId(assigneeId)
                    .build();
        }

        Ticket ticket = ticketService.transferTicket(currentUser, ticketId, assigneeId);
        return AgentToolResult.builder()
                .invoked(true)
                .reply("已转派工单。")
                .data(ticket)
                .activeTicketId(ticket.getId())
                .activeAssigneeId(assigneeId)
                .build();
    }

    private AgentToolResult searchHistory(AgentSessionContext context) {
        List<String> messages = context == null || context.getRecentMessages() == null
                ? List.of()
                : context.getRecentMessages();
        return AgentToolResult.builder()
                .invoked(true)
                .reply(messages.isEmpty() ? "当前会话暂无历史消息。" : "已查询当前会话历史。")
                .data(messages)
                .build();
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
        if (text.contains("账号") || text.contains("权限") || text.toLowerCase().contains("account")) {
            return TicketCategoryEnum.ACCOUNT;
        }
        if (text.contains("环境") || text.contains("配置") || text.toLowerCase().contains("environment")) {
            return TicketCategoryEnum.ENVIRONMENT;
        }
        if (text.contains("系统") || text.contains("功能") || text.toLowerCase().contains("system")) {
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

    private Long firstNumber(String message) {
        List<Long> values = numbers(message);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<Long> numbers(String message) {
        Matcher matcher = NUMBER_PATTERN.matcher(message == null ? "" : message);
        return matcher.results()
                .map(result -> Long.parseLong(result.group()))
                .toList();
    }
}
