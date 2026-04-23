package com.smartticket.agent.tool.ticket;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.support.AgentToolResults;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.biz.dto.ticket.TicketDetailDTO;
import com.smartticket.biz.dto.ticket.TicketPageQueryDTO;
import com.smartticket.biz.dto.ticket.TicketSummaryDTO;
import com.smartticket.biz.service.ticket.TicketQueryService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 查询当前工单事实数据的 Tool。
 * 这里只访问业务系统中的实时工单数据，不走知识库检索。
 */
@Component
public class QueryTicketTool implements AgentTool {
    private static final String NAME = "queryTicket";
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 5;

    /**
     * 工单查询服务，负责事实查询和权限判断。
     */
    private final TicketQueryService ticketQueryService;

    /**
     * Spring AI Tool Calling 适配支持。
     */
    private final SpringAiToolSupport springAiToolSupport;

    public QueryTicketTool(TicketQueryService ticketQueryService, @Lazy SpringAiToolSupport springAiToolSupport) {
        this.ticketQueryService = ticketQueryService;
        this.springAiToolSupport = springAiToolSupport;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean support(AgentIntent intent) {
        return intent == AgentIntent.QUERY_TICKET;
    }

    @Override
    public AgentToolMetadata metadata() {
        return AgentToolMetadata.builder()
                .name(NAME)
                .description("查询工单详情、工单摘要，或查看当前用户可见的工单列表")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .readOnly(true)
                .requireConfirmation(false)
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        if (summaryRequested(request)) {
            return summarizeTicket(request);
        }
        Long ticketId = request.getParameters().getTicketId();
        if (ticketId != null) {
            return queryTicketDetail(request, ticketId);
        }
        return queryVisibleTicketPage(request);
    }

    private boolean summaryRequested(AgentToolRequest request) {
        return Boolean.TRUE.equals(request.getParameters().getSummaryRequested());
    }

    private AgentToolResult queryTicketDetail(AgentToolRequest request, Long ticketId) {
        TicketDetailDTO detail = ticketQueryService.getDetail(request.getCurrentUser(), ticketId);
        return AgentToolResults.success(NAME, "已查询工单详情。", detail, ticketId, null);
    }

    private AgentToolResult queryVisibleTicketPage(AgentToolRequest request) {
        PageResult<Ticket> page = ticketQueryService.pageTickets(
                request.getCurrentUser(),
                TicketPageQueryDTO.builder()
                        .pageNo(DEFAULT_PAGE_NO)
                        .pageSize(DEFAULT_PAGE_SIZE)
                        .build()
        );
        return AgentToolResults.success(NAME, "已查询最近可见工单列表。", page);
    }

    private AgentToolResult summarizeTicket(AgentToolRequest request) {
        Long ticketId = request.getParameters().getTicketId();
        if (ticketId == null) {
            return AgentToolResults.failed(NAME, "请先指定需要摘要的工单 ID，或先查询某个工单后再继续提问。", null);
        }
        TicketSummaryDTO summary = ticketQueryService.getSummary(
                request.getCurrentUser(),
                ticketId,
                request.getParameters().getSummaryView()
        );
        return AgentToolResults.success(NAME, buildSummaryReply(summary), summary, ticketId, null);
    }

    /**
     * Spring AI Tool Calling 入口。
     *
     * @param ticketId 可选工单 ID；为空时查询当前用户可见工单列表
     * @param summaryRequested 是否返回工单摘要；为 true 时优先返回摘要
     * @param summaryView 摘要视角，支持 SUBMITTER、ASSIGNEE、ADMIN
     * @param toolContext Spring AI Tool 上下文
     * @return Tool 执行结果
     */
    @Tool(
            name = NAME,
            description = "查询当前工单事实数据，或按指定视角生成工单摘要。可按工单 ID 查详情；未提供 ID 时查询当前用户可见工单列表。该工具不检索历史知识库。"
    )
    public AgentToolResult queryTicket(
            @ToolParam(required = false, description = "工单 ID，为空时查询当前用户可见工单列表") Long ticketId,
            @ToolParam(required = false, description = "是否返回工单摘要；true 时优先返回摘要") Boolean summaryRequested,
            @ToolParam(required = false, description = "摘要视角，支持 SUBMITTER、ASSIGNEE、ADMIN") String summaryView,
            ToolContext toolContext
    ) {
        return springAiToolSupport.execute(
                this,
                toolContext,
                AgentIntent.QUERY_TICKET,
                buildToolParameters(ticketId, summaryRequested, summaryView)
        );
    }

    private AgentToolParameters buildToolParameters(Long ticketId, Boolean summaryRequested, String summaryView) {
        return AgentToolParameters.builder()
                .ticketId(ticketId)
                .summaryRequested(summaryRequested)
                .summaryView(parseSummaryView(summaryView))
                .build();
    }

    private TicketSummaryViewEnum parseSummaryView(String summaryView) {
        if (summaryView == null || summaryView.isBlank()) {
            return null;
        }
        try {
            return TicketSummaryViewEnum.fromCode(summaryView.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String buildSummaryReply(TicketSummaryDTO summary) {
        if (summary == null) {
            return "未生成摘要结果。";
        }
        return summary.getTitle() + "：" + summary.getSummary() + buildFirstHighlight(summary);
    }

    private String buildFirstHighlight(TicketSummaryDTO summary) {
        if (summary.getHighlights() == null || summary.getHighlights().isEmpty()) {
            return "";
        }
        return " 重点：" + summary.getHighlights().get(0);
    }
}
