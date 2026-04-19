package com.smartticket.agent.tool.ticket;

import com.smartticket.agent.model.AgentIntent;
import com.smartticket.agent.tool.core.AgentTool;
import com.smartticket.agent.tool.core.AgentToolMetadata;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.ToolRiskLevel;
import com.smartticket.agent.tool.support.AgentToolResults;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.service.TicketService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import org.springframework.stereotype.Component;

/**
 * 查询当前工单事实数据的 Tool。
 */
@Component
public class QueryTicketTool implements AgentTool {
    private static final String NAME = "queryTicket";

    private final TicketService ticketService;

    public QueryTicketTool(TicketService ticketService) {
        this.ticketService = ticketService;
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
        Long ticketId = request.getParameters().getTicketId();
        if (ticketId != null) {
            TicketDetailDTO detail = ticketService.getDetail(request.getCurrentUser(), ticketId);
            return AgentToolResults.success(NAME, "已查询工单详情。", detail, ticketId, null);
        }
        PageResult<Ticket> page = ticketService.pageTickets(request.getCurrentUser(), TicketPageQueryDTO.builder()
                .pageNo(1)
                .pageSize(5)
                .build());
        return AgentToolResults.success(NAME, "已查询最近可见工单列表。", page);
    }
}
