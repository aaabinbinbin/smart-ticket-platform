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
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.dto.TicketSummaryDTO;
import com.smartticket.biz.service.TicketQueryService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 查询当前工单事实数据的 Tool。
 *
 * <p>该 Tool 只访问当前业务事实，不调用 RAG。权限和可见性仍由 biz 层
 * {@link TicketQueryService} 判断。</p>
 */
@Component
public class QueryTicketTool implements AgentTool {
    private static final String NAME = "queryTicket";

    /**
     * 工单业务服务，负责事实查询和权限判断。
     */
    private final TicketQueryService ticketQueryService;

    /**
     * Spring AI Tool Calling 适配支持。
     */
    private final SpringAiToolSupport springAiToolSupport;

    public QueryTicketTool(TicketQueryService ticketQueryService, SpringAiToolSupport springAiToolSupport) {
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
                .description("查询工单详情或当前用户可见工单列表")
                .riskLevel(ToolRiskLevel.READ_ONLY)
                .readOnly(true)
                .requireConfirmation(false)
                .build();
    }

    @Override
    public AgentToolResult execute(AgentToolRequest request) {
        if (Boolean.TRUE.equals(request.getParameters().getSummaryRequested())) {
            return summarizeTicket(request);
        }
        Long ticketId = request.getParameters().getTicketId();
        if (ticketId != null) {
            TicketDetailDTO detail = ticketQueryService.getDetail(request.getCurrentUser(), ticketId);
            return AgentToolResults.success(NAME, "已查询工单详情。", detail, ticketId, null);
        }
        PageResult<Ticket> page = ticketQueryService.pageTickets(request.getCurrentUser(), TicketPageQueryDTO.builder()
                .pageNo(1)
                .pageSize(5)
                .build());
        return AgentToolResults.success(NAME, "已查询最近可见工单列表。", page);
    }

    private AgentToolResult summarizeTicket(AgentToolRequest request) {
        Long ticketId = request.getParameters().getTicketId();
        if (ticketId == null) {
            return AgentToolResults.failed(NAME, "请先指定要摘要的工单 ID，或先查询某个工单后再继续提问。", null);
        }
        TicketSummaryDTO summary = ticketQueryService.getSummary(
                request.getCurrentUser(),
                ticketId,
                request.getParameters().getSummaryView()
        );
        return AgentToolResults.success(
                NAME,
                buildSummaryReply(summary),
                summary,
                ticketId,
                null
        );
    }

    /**
     * Spring AI Tool Calling 入口。
     *
     * @param ticketId 可选工单 ID；为空时查询当前用户可见工单列表
     * @param toolContext Spring AI Tool 上下文
     * @return Tool 执行结果
     */
    @Tool(name = NAME, description = "查询当前工单事实数据，或按指定视角生成工单摘要。可按工单ID查详情；未提供ID时查询当前用户可见工单列表。该工具不检索历史知识库。")
    public AgentToolResult queryTicket(
            @ToolParam(required = false, description = "工单ID，为空时查询当前用户可见工单列表") Long ticketId,
            @ToolParam(required = false, description = "是否返回工单摘要；true 时优先返回摘要") Boolean summaryRequested,
            @ToolParam(required = false, description = "摘要视角：SUBMITTER、ASSIGNEE、ADMIN") String summaryView,
            ToolContext toolContext
    ) {
        return springAiToolSupport.execute(
                this,
                toolContext,
                AgentIntent.QUERY_TICKET,
                AgentToolParameters.builder()
                        .ticketId(ticketId)
                        .summaryRequested(summaryRequested)
                        .summaryView(parseSummaryView(summaryView))
                        .build()
        );
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
        String firstHighlight = summary.getHighlights() == null || summary.getHighlights().isEmpty()
                ? ""
                : " 重点：" + summary.getHighlights().get(0);
        return summary.getTitle() + "：" + summary.getSummary() + firstHighlight;
    }
}
