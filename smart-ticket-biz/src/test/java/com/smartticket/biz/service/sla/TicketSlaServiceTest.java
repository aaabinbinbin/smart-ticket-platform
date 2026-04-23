package com.smartticket.biz.service.sla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.sla.TicketSlaPolicyCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.sla.TicketSlaInstanceRepository;
import com.smartticket.biz.repository.sla.TicketSlaPolicyRepository;
import com.smartticket.biz.repository.ticket.TicketOperationLogRepository;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.ticket.TicketDetailCacheService;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketSlaInstance;
import com.smartticket.domain.entity.TicketSlaPolicy;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketSlaServiceTest {

    @Mock
    private TicketSlaPolicyRepository policyRepository;

    @Mock
    private TicketSlaInstanceRepository instanceRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketOperationLogRepository operationLogRepository;

    @Mock
    private TicketPermissionService permissionService;

    @Mock
    private TicketDetailCacheService ticketDetailCacheService;

    @Mock
    private TicketSlaNotificationService notificationService;

    @InjectMocks
    private TicketSlaService service;

    @Test
    void createPolicyShouldRejectResolveMinutesLessThanFirstResponse() {
        TicketSlaPolicyCommandDTO command = TicketSlaPolicyCommandDTO.builder()
                .policyName("high-priority")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .firstResponseMinutes(30)
                .resolveMinutes(20)
                .enabled(true)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createPolicy(adminUser(), command));

        assertEquals("INVALID_TICKET_SLA_POLICY", ex.getCode());
    }

    @Test
    void createOrRefreshInstanceShouldInsertCalculatedDeadlines() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 23, 12, 0);
        Ticket ticket = Ticket.builder()
                .id(1001L)
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .createdAt(createdAt)
                .build();
        when(policyRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(TicketSlaPolicy.builder()
                .id(7L)
                .firstResponseMinutes(30)
                .resolveMinutes(120)
                .build());
        when(instanceRepository.findByTicketId(1001L)).thenReturn(null);

        service.createOrRefreshInstance(ticket);

        ArgumentCaptor<com.smartticket.domain.entity.TicketSlaInstance> captor =
                ArgumentCaptor.forClass(com.smartticket.domain.entity.TicketSlaInstance.class);
        verify(instanceRepository).insert(captor.capture());
        assertEquals(1001L, captor.getValue().getTicketId());
        assertEquals(7L, captor.getValue().getPolicyId());
        assertEquals(createdAt.plusMinutes(30), captor.getValue().getFirstResponseDeadline());
        assertEquals(createdAt.plusMinutes(120), captor.getValue().getResolveDeadline());
        assertEquals(0, captor.getValue().getBreached());
    }

    @Test
    void scanBreachedInstancesShouldNotifyAfterMarkingBreach() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 23, 14, 0);
        TicketSlaInstance candidate = TicketSlaInstance.builder()
                .id(8L)
                .ticketId(1001L)
                .firstResponseDeadline(now.minusMinutes(5))
                .resolveDeadline(now.plusMinutes(30))
                .breached(0)
                .build();
        Ticket ticket = Ticket.builder()
                .id(1001L)
                .ticketNo("INC202604230001")
                .title("login error")
                .creatorId(11L)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .build();
        when(instanceRepository.findBreachedCandidates(now, 20)).thenReturn(List.of(candidate));
        when(ticketRepository.findById(1001L)).thenReturn(ticket, ticket);
        when(instanceRepository.markBreached(8L)).thenReturn(1);
        when(ticketRepository.findUsersByRoleCode("ADMIN")).thenReturn(List.of());
        when(ticketRepository.updatePriority(1001L, TicketPriorityEnum.URGENT)).thenReturn(1);

        service.scanBreachedInstances(adminUser(), now, 20);

        verify(notificationService).notifyBreached(eq(ticket), eq(candidate), eq("FIRST_RESPONSE"), eq(true));
    }

    private CurrentUser adminUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("admin1")
                .roles(List.of("ADMIN"))
                .build();
    }
}
