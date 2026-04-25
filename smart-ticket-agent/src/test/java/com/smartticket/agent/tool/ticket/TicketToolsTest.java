package com.smartticket.agent.tool.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.agent.model.AgentSessionContext;
import com.smartticket.agent.tool.core.AgentToolRequest;
import com.smartticket.agent.tool.core.AgentToolResult;
import com.smartticket.agent.tool.core.AgentToolStatus;
import com.smartticket.agent.tool.parameter.AgentToolParameterField;
import com.smartticket.agent.tool.parameter.AgentToolParameters;
import com.smartticket.agent.tool.parameter.AgentToolRequestValidator;
import com.smartticket.agent.tool.parameter.AgentToolValidationResult;
import com.smartticket.agent.tool.support.SpringAiToolSupport;
import com.smartticket.biz.dto.ticket.TicketDetailDTO;
import com.smartticket.biz.dto.ticket.TicketSummaryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.biz.service.ticket.TicketCommandService;
import com.smartticket.biz.service.ticket.TicketQueryService;
import com.smartticket.biz.service.ticket.TicketWorkflowService;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import com.smartticket.rag.model.RetrievalHit;
import com.smartticket.rag.model.RetrievalRequest;
import com.smartticket.rag.model.RetrievalResult;
import com.smartticket.rag.service.RetrievalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketToolsTest {

    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private TicketCommandService ticketCommandService;
    @Mock
    private TicketWorkflowService ticketWorkflowService;
    @Mock
    private AgentToolRequestValidator validator;
    @Mock
    private RetrievalService retrievalService;
    @Mock
    private SpringAiToolSupport springAiToolSupport;

    @Test
    void queryTicketToolShouldReadDetailInsteadOfListingWhenTicketIdExists() {
        QueryTicketTool tool = new QueryTicketTool(ticketQueryService, springAiToolSupport);
        when(ticketQueryService.getDetail(currentUser(), 1001L)).thenReturn(TicketDetailDTO.builder()
                .ticket(ticket(1001L))
                .build());

        AgentToolResult result = tool.execute(AgentToolRequest.builder()
                .currentUser(currentUser())
                .parameters(AgentToolParameters.builder().ticketId(1001L).build())
                .build());

        assertEquals(AgentToolStatus.SUCCESS, result.getStatus());
        assertEquals(1001L, result.getActiveTicketId());
        verify(ticketQueryService).getDetail(currentUser(), 1001L);
        verify(ticketQueryService, never()).pageTickets(any(), any());
    }

    @Test
    void createTicketToolShouldReturnNeedMoreInfoWhenRequiredFieldsMissing() {
        CreateTicketTool tool = new CreateTicketTool(ticketCommandService, validator, retrievalService, springAiToolSupport, 0.72d);
        when(validator.validate(eq(tool), any())).thenReturn(AgentToolValidationResult.builder()
                .valid(false)
                .missingFields(List.of(AgentToolParameterField.TITLE))
                .build());

        AgentToolResult result = tool.execute(AgentToolRequest.builder()
                .currentUser(currentUser())
                .parameters(AgentToolParameters.builder().build())
                .build());

        assertEquals(AgentToolStatus.NEED_MORE_INFO, result.getStatus());
        verify(retrievalService, never()).checkSimilarCasesBeforeCreate(any(), any(), any());
        verify(ticketCommandService, never()).createTicket(any(), any());
    }

    @Test
    void transferTicketToolShouldUseContextTicketIdWhenOnlyAssigneeIsProvided() {
        TransferTicketTool tool = new TransferTicketTool(ticketWorkflowService, validator, springAiToolSupport);
        when(validator.validate(eq(tool), any())).thenReturn(AgentToolValidationResult.builder().valid(true).build());
        when(ticketWorkflowService.transferTicket(currentUser(), 1001L, 3L)).thenReturn(ticket(1001L));

        AgentToolResult result = tool.execute(AgentToolRequest.builder()
                .currentUser(currentUser())
                .message("转给3")
                .context(AgentSessionContext.builder().activeTicketId(1001L).build())
                .parameters(AgentToolParameters.builder().numbers(List.of(3L)).build())
                .build());

        assertEquals(AgentToolStatus.SUCCESS, result.getStatus());
        assertEquals(1001L, result.getActiveTicketId());
        assertEquals(3L, result.getActiveAssigneeId());
        verify(ticketWorkflowService).transferTicket(currentUser(), 1001L, 3L);
    }

    @Test
    void createTicketToolShouldRecordAlreadyTriedBranch() {
        CreateTicketTool tool = new CreateTicketTool(ticketCommandService, validator, retrievalService, springAiToolSupport, 0.72d);
        when(validator.validate(eq(tool), any())).thenReturn(AgentToolValidationResult.builder().valid(true).build());
        when(retrievalService.checkSimilarCasesBeforeCreate(any(), any(), eq(3))).thenReturn(RetrievalResult.builder()
                .retrievalPath("MYSQL_FALLBACK")
                .fallbackUsed(true)
                .hits(List.of(RetrievalHit.builder().score(0.88d).build()))
                .build());
        when(ticketCommandService.createTicket(any(), any())).thenReturn(ticket(1003L));

        AgentToolResult result = tool.execute(AgentToolRequest.builder()
                .currentUser(currentUser())
                .message("创建工单，我已经试过重启还是没用")
                .parameters(AgentToolParameters.builder()
                        .title("登录失败")
                        .description("已经试过重启还是没用")
                        .category(TicketCategoryEnum.SYSTEM)
                        .priority(TicketPriorityEnum.HIGH)
                        .build())
                .build());

        assertEquals(AgentToolStatus.SUCCESS, result.getStatus());
        assertTrue(result.getData() instanceof Map<?, ?>);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(Boolean.TRUE, data.get("userAlreadyTried"));
        assertEquals(Boolean.FALSE, data.get("deflectionSucceeded"));
    }

    @Test
    void searchHistoryToolShouldCallRetrievalServiceAndReturnRecentMessages() {
        SearchHistoryTool tool = new SearchHistoryTool(retrievalService, springAiToolSupport);
        RetrievalResult retrievalResult = RetrievalResult.builder()
                .queryText("登录失败")
                .hits(List.of(RetrievalHit.builder().knowledgeId(1L).score(0.88d).build()))
                .build();
        when(retrievalService.retrieve(any(RetrievalRequest.class))).thenReturn(retrievalResult);

        AgentToolResult result = tool.execute(AgentToolRequest.builder()
                .message("原始消息")
                .context(AgentSessionContext.builder().recentMessages(List.of("上一轮消息")).build())
                .parameters(AgentToolParameters.builder().description("登录失败").build())
                .build());

        assertEquals(AgentToolStatus.SUCCESS, result.getStatus());
        assertTrue(result.getData() instanceof Map<?, ?>);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(retrievalResult, data.get("retrieval"));
        assertEquals(List.of("上一轮消息"), data.get("recentMessages"));
        verify(retrievalService).retrieve(any(RetrievalRequest.class));
    }

    @Test
    void queryTicketToolShouldListVisibleTicketsWhenNoTicketIdProvided() {
        QueryTicketTool tool = new QueryTicketTool(ticketQueryService, springAiToolSupport);
        when(ticketQueryService.pageTickets(eq(currentUser()), any())).thenReturn(PageResult.<Ticket>builder()
                .records(List.of(ticket(1002L)))
                .total(1L)
                .pageNo(1)
                .pageSize(5)
                .build());

        AgentToolResult result = tool.execute(AgentToolRequest.builder()
                .currentUser(currentUser())
                .parameters(AgentToolParameters.builder().build())
                .build());

        assertEquals(AgentToolStatus.SUCCESS, result.getStatus());
        verify(ticketQueryService).pageTickets(eq(currentUser()), any());
    }

    @Test
    void queryTicketToolShouldReturnSummaryWhenSummaryRequested() {
        QueryTicketTool tool = new QueryTicketTool(ticketQueryService, springAiToolSupport);
        when(ticketQueryService.getSummary(currentUser(), 1006L, TicketSummaryViewEnum.ADMIN)).thenReturn(TicketSummaryDTO.builder()
                .view(TicketSummaryViewEnum.ADMIN)
                .title("管理员风险摘要")
                .summary("当前风险等级为高，需关注审批和处理推进。")
                .highlights(List.of("审批仍未完成"))
                .riskLevel("HIGH")
                .build());

        AgentToolResult result = tool.execute(AgentToolRequest.builder()
                .currentUser(currentUser())
                .parameters(AgentToolParameters.builder()
                        .ticketId(1006L)
                        .summaryRequested(true)
                        .summaryView(TicketSummaryViewEnum.ADMIN)
                        .build())
                .build());

        assertEquals(AgentToolStatus.SUCCESS, result.getStatus());
        assertEquals(1006L, result.getActiveTicketId());
        assertTrue(result.getReply().contains("管理员风险摘要"));
        verify(ticketQueryService).getSummary(currentUser(), 1006L, TicketSummaryViewEnum.ADMIN);
        verify(ticketQueryService, never()).getDetail(any(), any());
    }

    private CurrentUser currentUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER", "STAFF"))
                .build();
    }

    private Ticket ticket(Long id) {
        return Ticket.builder()
                .id(id)
                .ticketNo("INC" + id)
                .title("测试工单")
                .description("测试描述")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .status(TicketStatusEnum.PROCESSING)
                .creatorId(1L)
                .assigneeId(2L)
                .source("MANUAL")
                .build();
    }
}
