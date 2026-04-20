package com.smartticket.agent.tool.ticket;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.parameter.AgentToolRequestValidator;
import com.smartticket.agent.tool.parameter.AgentToolValidationResult;
import com.smartticket.agent.tool.support.AgentToolResults;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.biz.service.TicketService;
import com.smartticket.domain.entity.Ticket;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 转派工单 Tool。
 *
 * <p>该 Tool 是高风险写操作，执行前必须经过 Agent Guard。真正转派动作仍由
 * {@link TicketService#transferTicket} 完成，权限、状态和目标处理人合法性由 biz 层判断。</p>
 */
@Component
public class TransferTicketTool implements AgentTool {
    private static final String NAME = "transferTicket";

    /**
     * 工单业务服务。
     */
    private final TicketService ticketService;

    /**
     * Tool 参数校验器。
     */
    private final AgentToolRequestValidator validator;

    /**
     * Spring AI Tool Calling 适配支持。
     */
    private final SpringAiToolSupport springAiToolSupport;

    public TransferTicketTool(
            TicketService ticketService,
            AgentToolRequestValidator validator,
            SpringAiToolSupport springAiToolSupport
    ) {
        this.ticketService = ticketService;
        this.validator = validator;
        this.springAiToolSupport = springAiToolSupport;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean support(AgentIntent intent) {
        return intent == AgentIntent.TRANSFER_TICKET;
    }

    @Override
    public AgentToolMetadata metadata() {
        return AgentToolMetadata.builder()
                .name(NAME)
                .description("将处理中的工单转派给目标处理人")
                .riskLevel(ToolRiskLevel.HIGH_RISK_WRITE)
                .readOnly(false)
                .requireConfirmation(true)
                .requiredFields(List.of(
                        AgentToolParameterField.TICKET_ID,
                        AgentToolParameterField.ASSIGNEE_ID
                ))
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        normalizeSingleNumberTransfer(request);
        AgentToolValidationResult validation = validator.validate(this, request);
        if (!validation.isValid()) {
            return AgentToolResults.needMoreInfo(NAME, validation.getMissingFields());
        }

        Long ticketId = request.getParameters().getTicketId();
        Long assigneeId = request.getParameters().getAssigneeId();
        Ticket ticket = ticketService.transferTicket(request.getCurrentUser(), ticketId, assigneeId);
        return AgentToolResults.success(NAME, "已转派工单。", ticket, ticket.getId(), assigneeId);
    }

    /**
     * Spring AI Tool Calling 入口。
     *
     * @param ticketId 工单 ID
     * @param assigneeId 目标处理人 ID
     * @param toolContext Spring AI Tool 上下文
     * @return Tool 执行结果
     */
    @Tool(name = NAME, description = "将处理中的工单转派给目标处理人。该写操作必须经过确认，并由biz层判断权限和状态是否合法。")
    public AgentToolResult transferTicket(
            @ToolParam(required = false, description = "工单ID；为空时可从会话上下文中的当前工单推断") Long ticketId,
            @ToolParam(description = "目标处理人用户ID，必须是有效STAFF") Long assigneeId,
            ToolContext toolContext
    ) {
        return springAiToolSupport.execute(
                this,
                toolContext,
                AgentIntent.TRANSFER_TICKET,
                AgentToolParameters.builder()
                        .ticketId(ticketId)
                        .assigneeId(assigneeId)
                        .numbers(assigneeId == null ? List.of() : List.of(assigneeId))
                        .build()
        );
    }

    /**
     * 如果用户只给出一个数字且上下文中已有当前工单，则把该数字解释为目标处理人。
     */
    private void normalizeSingleNumberTransfer(AgentToolRequest request) {
        if (request.getParameters().getNumbers().size() != 1 || request.getParameters().getAssigneeId() != null) {
            return;
        }
        Long onlyNumber = request.getParameters().getNumbers().get(0);
        if (request.getContext() != null && request.getContext().getActiveTicketId() != null) {
            request.getParameters().setTicketId(request.getContext().getActiveTicketId());
            request.getParameters().setAssigneeId(onlyNumber);
            return;
        }
        if (looksLikeAssigneeOnly(request.getMessage())) {
            request.getParameters().setTicketId(null);
            request.getParameters().setAssigneeId(onlyNumber);
        }
    }

    /**
     * 判断消息是否更像只提供了处理人信息。
     */
    private boolean looksLikeAssigneeOnly(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("给") || message.contains("处理人") || message.toLowerCase().contains("assignee");
    }
}
