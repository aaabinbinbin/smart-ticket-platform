package com.smartticket.biz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.dto.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    @Mock
    private TicketCommandService ticketCommandService;
    @Mock
    private TicketQueryService ticketQueryService;
    @Mock
    private TicketWorkflowService ticketWorkflowService;
    @Mock
    private TicketCommentService ticketCommentService;
    @Mock
    private TicketQueueBindingService ticketQueueBindingService;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                ticketCommandService,
                ticketQueryService,
                ticketWorkflowService,
                ticketCommentService,
                ticketQueueBindingService
        );
    }

    @Test
    @DisplayName("createTicket 应委派给 TicketCommandService")
    void createTicketShouldDelegateToCommandService() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .build();
        Ticket expected = ticket(1001L);
        when(ticketCommandService.createTicket(currentUser(), command)).thenReturn(expected);

        Ticket result = ticketService.createTicket(currentUser(), command);

        assertEquals(expected, result);
        verify(ticketCommandService).createTicket(currentUser(), command);
    }

    @Test
    @DisplayName("getDetail 应委派给 TicketQueryService")
    void getDetailShouldDelegateToQueryService() {
        TicketDetailDTO expected = TicketDetailDTO.builder().ticket(ticket(1002L)).build();
        when(ticketQueryService.getDetail(currentUser(), 1002L)).thenReturn(expected);

        TicketDetailDTO result = ticketService.getDetail(currentUser(), 1002L);

        assertEquals(expected, result);
        verify(ticketQueryService).getDetail(currentUser(), 1002L);
    }

    @Test
    @DisplayName("pageTickets 应委派给 TicketQueryService")
    void pageTicketsShouldDelegateToQueryService() {
        TicketPageQueryDTO query = TicketPageQueryDTO.builder()
                .pageNo(1)
                .pageSize(10)
                .status(TicketStatusEnum.PROCESSING)
                .build();
        PageResult<Ticket> expected = PageResult.<Ticket>builder()
                .pageNo(1)
                .pageSize(10)
                .total(1L)
                .records(List.of(ticket(1003L)))
                .build();
        when(ticketQueryService.pageTickets(currentUser(), query)).thenReturn(expected);

        PageResult<Ticket> result = ticketService.pageTickets(currentUser(), query);

        assertEquals(expected, result);
        verify(ticketQueryService).pageTickets(currentUser(), query);
    }

    @Test
    @DisplayName("工作流操作应委派给 TicketWorkflowService")
    void workflowOperationsShouldDelegateToWorkflowService() {
        Ticket expected = ticket(1004L);
        TicketUpdateStatusCommandDTO command = TicketUpdateStatusCommandDTO.builder()
                .targetStatus(TicketStatusEnum.RESOLVED)
                .solutionSummary("重启服务后恢复")
                .build();
        when(ticketWorkflowService.assignTicket(currentUser(), 1004L, 2L)).thenReturn(expected);
        when(ticketWorkflowService.claimTicket(currentUser(), 1004L)).thenReturn(expected);
        when(ticketWorkflowService.transferTicket(currentUser(), 1004L, 3L)).thenReturn(expected);
        when(ticketWorkflowService.updateStatus(currentUser(), 1004L, command)).thenReturn(expected);
        when(ticketWorkflowService.closeTicket(currentUser(), 1004L)).thenReturn(expected);

        assertEquals(expected, ticketService.assignTicket(currentUser(), 1004L, 2L));
        assertEquals(expected, ticketService.claimTicket(currentUser(), 1004L));
        assertEquals(expected, ticketService.transferTicket(currentUser(), 1004L, 3L));
        assertEquals(expected, ticketService.updateStatus(currentUser(), 1004L, command));
        assertEquals(expected, ticketService.closeTicket(currentUser(), 1004L));

        verify(ticketWorkflowService).assignTicket(currentUser(), 1004L, 2L);
        verify(ticketWorkflowService).claimTicket(currentUser(), 1004L);
        verify(ticketWorkflowService).transferTicket(currentUser(), 1004L, 3L);
        verify(ticketWorkflowService).updateStatus(currentUser(), 1004L, command);
        verify(ticketWorkflowService).closeTicket(currentUser(), 1004L);
    }

    @Test
    @DisplayName("评论与队列绑定操作应委派给对应服务")
    void commentAndQueueBindingShouldDelegateToSpecificServices() {
        Ticket expectedTicket = ticket(1005L);
        TicketComment expectedComment = TicketComment.builder()
                .id(200L)
                .ticketId(1005L)
                .commenterId(1L)
                .content("正在排查日志")
                .build();
        when(ticketCommentService.addComment(currentUser(), 1005L, "正在排查日志")).thenReturn(expectedComment);
        when(ticketQueueBindingService.bindTicketQueue(currentUser(), 1005L, 30L, 40L)).thenReturn(expectedTicket);

        assertEquals(expectedComment, ticketService.addComment(currentUser(), 1005L, "正在排查日志"));
        assertEquals(expectedTicket, ticketService.bindTicketQueue(currentUser(), 1005L, 30L, 40L));

        verify(ticketCommentService).addComment(currentUser(), 1005L, "正在排查日志");
        verify(ticketQueueBindingService).bindTicketQueue(currentUser(), 1005L, 30L, 40L);
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
