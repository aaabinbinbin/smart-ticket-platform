package com.smartticket.agent.tool.ticket;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolRequestValidator;
import com.smartticket.agent.tool.parameter.AgentToolValidationResult;
import com.smartticket.agent.tool.support.AgentToolResults;
import com.smartticket.biz.service.TicketService;
import com.smartticket.domain.entity.Ticket;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 转派工单 Tool。
 */
@Component
public class TransferTicketTool implements AgentTool {
    private static final String NAME = "transferTicket";

    private final TicketService ticketService;
    private final AgentToolRequestValidator validator;

    public TransferTicketTool(TicketService ticketService, AgentToolRequestValidator validator) {
        this.ticketService = ticketService;
        this.validator = validator;
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

    private boolean looksLikeAssigneeOnly(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("给") || message.contains("处理人") || message.toLowerCase().contains("assignee");
    }
}
