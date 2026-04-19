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
import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.service.TicketService;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.service.RetrievalService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 创建工单 Tool。
 */
@Component
public class CreateTicketTool implements AgentTool {
    private static final String NAME = "createTicket";

    private final TicketService ticketService;
    private final AgentToolRequestValidator validator;
    private final RetrievalService retrievalService;

    public CreateTicketTool(
            TicketService ticketService,
            AgentToolRequestValidator validator,
            RetrievalService retrievalService
    ) {
        this.ticketService = ticketService;
        this.validator = validator;
        this.retrievalService = retrievalService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean support(AgentIntent intent) {
        return intent == AgentIntent.CREATE_TICKET;
    }

    @Override
    public AgentToolMetadata metadata() {
        return AgentToolMetadata.builder()
                .name(NAME)
                .description("根据用户消息创建工单")
                .riskLevel(ToolRiskLevel.LOW_RISK_WRITE)
                .readOnly(false)
                .requireConfirmation(false)
                .requiredFields(List.of(
                        AgentToolParameterField.TITLE,
                        AgentToolParameterField.DESCRIPTION,
                        AgentToolParameterField.CATEGORY,
                        AgentToolParameterField.PRIORITY
                ))
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        AgentToolValidationResult validation = validator.validate(this, request);
        if (!validation.isValid()) {
            return AgentToolResults.needMoreInfo(NAME, validation.getMissingFields());
        }

        RetrievalResult similarCases = retrievalService.checkSimilarCasesBeforeCreate(
                request.getParameters().getTitle(),
                request.getParameters().getDescription(),
                3
        );

        Ticket ticket = ticketService.createTicket(request.getCurrentUser(), TicketCreateCommandDTO.builder()
                .title(request.getParameters().getTitle())
                .description(request.getParameters().getDescription())
                .category(request.getParameters().getCategory())
                .priority(request.getParameters().getPriority())
                .build());
        String reply = similarCases.getHits().isEmpty()
                ? "已创建工单。"
                : "已创建工单，并找到相似历史案例供处理时参考。相似案例不会阻止本次创建。";
        return AgentToolResults.success(
                NAME,
                reply,
                Map.of(
                        "ticket", ticket,
                        "similarCases", similarCases
                ),
                ticket.getId(),
                null
        );
    }
}
