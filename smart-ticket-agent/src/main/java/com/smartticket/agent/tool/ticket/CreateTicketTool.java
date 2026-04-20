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
import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.service.TicketService;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.service.RetrievalService;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 创建工单 Tool。
 *
 * <p>该 Tool 可以做创建前相似案例参考，但真正创建动作必须通过 biz 层
 * {@link TicketService#createTicket} 完成，不直接写 repository。</p>
 */
@Component
public class CreateTicketTool implements AgentTool {
    private static final String NAME = "createTicket";

    /**
     * 工单业务服务，负责创建、幂等、日志和业务校验。
     */
    private final TicketService ticketService;

    /**
     * Tool 参数校验器。
     */
    private final AgentToolRequestValidator validator;

    /**
     * 历史知识检索服务，用于创建前相似案例参考。
     */
    private final RetrievalService retrievalService;

    /**
     * Spring AI Tool Calling 适配支持。
     */
    private final SpringAiToolSupport springAiToolSupport;

    public CreateTicketTool(
            TicketService ticketService,
            AgentToolRequestValidator validator,
            RetrievalService retrievalService,
            SpringAiToolSupport springAiToolSupport
    ) {
        this.ticketService = ticketService;
        this.validator = validator;
        this.retrievalService = retrievalService;
        this.springAiToolSupport = springAiToolSupport;
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
                .idempotencyKey(request.getParameters().getIdempotencyKey())
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

    /**
     * Spring AI Tool Calling 入口。
     *
     * @param title 工单标题
     * @param description 问题描述
     * @param category 工单分类编码
     * @param priority 优先级编码
     * @param idempotencyKey 可选幂等键
     * @param toolContext Spring AI Tool 上下文
     * @return Tool 执行结果
     */
    @Tool(name = NAME, description = "创建新工单。写操作必须通过biz层TicketService执行，并复用创建幂等逻辑。")
    public AgentToolResult createTicket(
            @ToolParam(description = "工单标题") String title,
            @ToolParam(description = "问题描述") String description,
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
                        .category(parseCategory(category))
                        .priority(parsePriority(priority))
                        .idempotencyKey(idempotencyKey)
                        .build()
        );
    }

    /**
     * 解析工单分类，非法或为空时使用 OTHER 兜底。
     */
    private TicketCategoryEnum parseCategory(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TicketCategoryEnum.OTHER;
        }
        try {
            return TicketCategoryEnum.fromCode(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TicketCategoryEnum.OTHER;
        }
    }

    /**
     * 解析优先级，非法或为空时使用 MEDIUM 兜底。
     */
    private TicketPriorityEnum parsePriority(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TicketPriorityEnum.MEDIUM;
        }
        try {
            return TicketPriorityEnum.fromCode(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TicketPriorityEnum.MEDIUM;
        }
    }
}
