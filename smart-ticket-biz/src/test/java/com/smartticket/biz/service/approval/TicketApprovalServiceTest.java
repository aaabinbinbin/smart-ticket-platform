package com.smartticket.biz.service.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.approval.TicketApprovalRepository;
import com.smartticket.biz.repository.approval.TicketApprovalStepRepository;
import com.smartticket.biz.service.ticket.TicketDetailCacheService;
import com.smartticket.biz.service.ticket.TicketServiceSupport;
import com.smartticket.biz.service.ticket.TicketUserDirectoryService;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.entity.TicketApprovalStep;
import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketApprovalServiceTest {

    @Mock
    private TicketServiceSupport support;

    @Mock
    private TicketApprovalRepository ticketApprovalRepository;

    @Mock
    private TicketApprovalStepRepository ticketApprovalStepRepository;

    @Mock
    private TicketApprovalTemplateService ticketApprovalTemplateService;

    @Mock
    private TicketApprovalStepFactory ticketApprovalStepFactory;

    @Mock
    private TicketUserDirectoryService ticketUserDirectoryService;

    @Mock
    private TicketDetailCacheService ticketDetailCacheService;

    @InjectMocks
    private TicketApprovalService service;

    @Test
    void requireApprovalPassedShouldRejectMissingApprovalRecord() {
        Ticket ticket = Ticket.builder()
                .id(1001L)
                .type(TicketTypeEnum.ACCESS_REQUEST)
                .build();
        when(ticketApprovalRepository.findByTicketId(1001L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.requireApprovalPassed(ticket));

        assertEquals("TICKET_APPROVAL_REQUIRED", ex.getCode());
    }

    @Test
    void approveShouldRejectNonCurrentApprover() {
        Ticket ticket = Ticket.builder()
                .id(1002L)
                .type(TicketTypeEnum.CHANGE_REQUEST)
                .creatorId(1L)
                .build();
        TicketApproval approval = TicketApproval.builder()
                .ticketId(1002L)
                .approvalStatus(TicketApprovalStatusEnum.PENDING)
                .build();
        TicketApprovalStep currentStep = TicketApprovalStep.builder()
                .id(11L)
                .ticketId(1002L)
                .approverId(99L)
                .stepOrder(1)
                .stepStatus(TicketApprovalStepStatusEnum.PENDING)
                .build();
        when(support.requireTicket(1002L)).thenReturn(ticket);
        when(ticketApprovalRepository.findByTicketId(1002L)).thenReturn(approval);
        when(ticketApprovalStepRepository.findCurrentPendingByTicketId(1002L)).thenReturn(currentStep);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(operator(), 1002L, "ok"));

        assertEquals("TICKET_APPROVAL_FORBIDDEN", ex.getCode());
        verify(support).requireTicket(1002L);
    }

    private CurrentUser operator() {
        return CurrentUser.builder()
                .userId(2L)
                .username("staff1")
                .roles(List.of("STAFF"))
                .build();
    }
}
