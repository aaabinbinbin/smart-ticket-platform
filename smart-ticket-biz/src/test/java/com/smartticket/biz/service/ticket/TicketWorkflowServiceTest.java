package com.smartticket.biz.service.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.ticket.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.approval.TicketApprovalService;
import com.smartticket.biz.service.assignment.TicketGroupService;
import com.smartticket.biz.service.assignment.TicketQueueMemberService;
import com.smartticket.biz.service.sla.TicketSlaService;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketWorkflowServiceTest {

    @Mock
    private TicketServiceSupport support;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketDetailCacheService ticketDetailCacheService;

    @Mock
    private TicketSlaService ticketSlaService;

    @Mock
    private TicketGroupService ticketGroupService;

    @Mock
    private TicketQueueMemberService ticketQueueMemberService;

    @Mock
    private TicketApprovalService ticketApprovalService;

    @InjectMocks
    private TicketWorkflowService service;

    @Test
    void updateStatusShouldRejectProcessingWithoutAssignee() {
        Ticket ticket = Ticket.builder()
                .id(1001L)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .assigneeId(null)
                .build();
        when(support.requireTicket(1001L)).thenReturn(ticket);
        when(support.permissionService()).thenReturn(new TicketPermissionService());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateStatus(
                adminUser(),
                1001L,
                TicketUpdateStatusCommandDTO.builder().targetStatus(TicketStatusEnum.PROCESSING).build()
        ));

        assertEquals("TICKET_ASSIGNEE_REQUIRED", ex.getCode());
        verify(ticketApprovalService).requireApprovalPassed(ticket);
    }

    @Test
    void updateStatusShouldRejectUsingCloseStatusViaStatusApi() {
        Ticket ticket = Ticket.builder()
                .id(1002L)
                .status(TicketStatusEnum.PROCESSING)
                .creatorId(1L)
                .build();
        when(support.requireTicket(1002L)).thenReturn(ticket);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateStatus(
                operator(),
                1002L,
                TicketUpdateStatusCommandDTO.builder().targetStatus(TicketStatusEnum.CLOSED).build()
        ));

        assertEquals("CLOSE_TICKET_USE_CLOSE_API", ex.getCode());
    }

    private CurrentUser adminUser() {
        return CurrentUser.builder()
                .userId(9L)
                .username("admin1")
                .roles(List.of("ADMIN"))
                .build();
    }

    private CurrentUser operator() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
