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
import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.biz.service.ticket.TicketCommandService;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.service.RetrievalService;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 创建工单 Tool。
 *
 * <p>支持在创建前检索相似案例，但真正的创建逻辑仍由 biz 层负责。</p>
 */
@Component
public class CreateTicketTool implements AgentTool {
    private static final String NAME = "createTicket";

    private final TicketCommandService ticketCommandService;
    private final AgentToolRequestValidator validator;
    private final RetrievalService retrievalService;
    private final SpringAiToolSupport springAiToolSupport;
    private final double deflectionThreshold;

    public CreateTicketTool(
            TicketCommandService ticketCommandService,
            AgentToolRequestValidator validator,
            RetrievalService retrievalService,
            @Lazy SpringAiToolSupport springAiToolSupport,
            @Value("${smart-ticket.agent.create.deflection-threshold:0.72}") double deflectionThreshold
    ) {
        this.ticketCommandService = ticketCommandService;
        this.validator = validator;
        this.retrievalService = retrievalService;
        this.springAiToolSupport = springAiToolSupport;
        this.deflectionThreshold = deflectionThreshold;
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
                        AgentToolParameterField.DESCRIPTION
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
        boolean userAlreadyTried = userAlreadyTriedSolution(request.getMessage(), request.getParameters().getDescription());
        boolean deflectionSuggested = shouldSuggestDeflection(similarCases);
        boolean deflectionSucceeded = deflectionSuggested && !userAlreadyTried;

        Ticket ticket = ticketCommandService.createTicket(request.getCurrentUser(), TicketCreateCommandDTO.builder()
                .title(request.getParameters().getTitle())
                .description(request.getParameters().getDescription())
                .type(request.getParameters().getType())
                .category(request.getParameters().getCategory())
                .priority(request.getParameters().getPriority())
                .idempotencyKey(request.getParameters().getIdempotencyKey())
                .build());
        String reply = buildReply(similarCases, deflectionSuggested, userAlreadyTried);
        return AgentToolResults.success(
                NAME,
                reply,
                Map.of(
                        "ticket", ticket,
                        "similarCases", similarCases,
                        "deflectionSuggested", deflectionSuggested,
                        "deflectionSucceeded", deflectionSucceeded,
                        "userAlreadyTried", userAlreadyTried,
                        "similarityThreshold", deflectionThreshold
                ),
                ticket.getId(),
                null
        );
    }

    private String buildReply(RetrievalResult similarCases, boolean deflectionSuggested, boolean userAlreadyTried) {
        if (similarCases.getHits().isEmpty()) {
            return "已创建工单。";
        }
        if (deflectionSuggested && userAlreadyTried) {
            return "已创建工单。检测到相似历史方案，但你已经说明试过相关处理，因此未做分流拦截，仅附上相似案例供处理人参考。";
        }
        if (deflectionSuggested) {
            return "已创建工单，并附上高相关历史案例。当前结果可用于创建前分流参考，但不会阻止本次创建。";
        }
        return "已创建工单，并找到相似历史案例供处理时参考。相似案例不会阻止本次创建。";
    }

    private boolean shouldSuggestDeflection(RetrievalResult similarCases) {
        if (similarCases == null || similarCases.getHits().isEmpty()) {
            return false;
        }
        Double score = similarCases.getHits().get(0).getScore();
        return score != null && score >= deflectionThreshold;
    }

    private boolean userAlreadyTriedSolution(String message, String description) {
        String text = (message == null ? "" : message) + " " + (description == null ? "" : description);
        return text.contains("试过")
                || text.contains("已经试")
                || text.contains("没用")
                || text.contains("无效")
                || text.toLowerCase().contains("already tried");
    }

    @Tool(name = NAME, description = "创建新工单，内部会复用业务层的幂等和校验逻辑")
    public AgentToolResult createTicket(
            @ToolParam(description = "工单标题") String title,
            @ToolParam(description = "问题描述") String description,
            @ToolParam(required = false, description = "工单类型编码：INCIDENT、ACCESS_REQUEST、ENVIRONMENT_REQUEST、CONSULTATION、CHANGE_REQUEST") String type,
            @ToolParam(required = false, description = "工单分类编码：ACCOUNT、SYSTEM、ENVIRONMENT、OTHER") String category,
            @ToolParam(required = false, description = "优先级编码：LOW、MEDIUM、HIGH、URGENT") String priority,
            @ToolParam(required = false, description = "创建幂等键，相同用户和相同幂等键重复提交时返回第一次创建结果") String idempotencyKey,
            ToolContext toolContext
    ) {
        return springAiToolSupport.execute(
                this,
                toolContext,
                AgentIntent.CREATE_TICKET,
                AgentToolParameters.builder()
                        .title(title)
                        .description(description)
                        .type(parseType(type))
                        .category(parseCategory(category))
                        .priority(parsePriority(priority))
                        .idempotencyKey(idempotencyKey)
                        .build()
        );
    }

    private TicketTypeEnum parseType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return TicketTypeEnum.fromCode(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private TicketCategoryEnum parseCategory(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return TicketCategoryEnum.fromCode(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private TicketPriorityEnum parsePriority(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return TicketPriorityEnum.fromCode(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
