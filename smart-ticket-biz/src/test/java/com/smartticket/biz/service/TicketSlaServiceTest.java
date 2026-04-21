package com.smartticket.biz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.TicketSlaPolicyCommandDTO;
import com.smartticket.biz.dto.TicketSlaScanResultDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketOperationLogRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.biz.repository.TicketSlaInstanceRepository;
import com.smartticket.biz.repository.TicketSlaPolicyRepository;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketSlaInstance;
import com.smartticket.domain.entity.TicketSlaPolicy;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketSlaServiceTest {
    private static final Long POLICY_ID = 10L;
    private static final Long TICKET_ID = 100L;

    @Mock private TicketSlaPolicyRepository policyRepository;
    @Mock private TicketSlaInstanceRepository instanceRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketOperationLogRepository operationLogRepository;
    @Mock private TicketDetailCacheService ticketDetailCacheService;
    @Mock private TicketSlaNotificationService notificationService;

    private TicketSlaService ticketSlaService;

    @BeforeEach
    void setUp() {
        ticketSlaService = new TicketSlaService(policyRepository, instanceRepository, ticketRepository, operationLogRepository, new TicketPermissionService(), ticketDetailCacheService, notificationService);
    }

    @Test
    @DisplayName("SLA策略: ADMIN 可以创建合法策略")
    void createPolicyShouldAllowAdmin() {
        when(policyRepository.insert(any(TicketSlaPolicy.class))).thenAnswer(invocation -> {
            TicketSlaPolicy policy = invocation.getArgument(0);
            policy.setId(POLICY_ID);
            return 1;
        });
        when(policyRepository.findById(POLICY_ID)).thenReturn(policy());

        TicketSlaPolicy result = ticketSlaService.createPolicy(admin(), TicketSlaPolicyCommandDTO.builder()
                .policyName("高优先级系统问题")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .firstResponseMinutes(30)
                .resolveMinutes(240)
                .enabled(true)
                .build());

        assertEquals(POLICY_ID, result.getId());
        ArgumentCaptor<TicketSlaPolicy> captor = ArgumentCaptor.forClass(TicketSlaPolicy.class);
        verify(policyRepository).insert(captor.capture());
        assertEquals("SYSTEM", captor.getValue().getCategory());
        assertEquals("HIGH", captor.getValue().getPriority());
    }

    @Test
    @DisplayName("SLA策略: 解决时限不能小于首次响应时限")
    void createPolicyShouldRejectInvalidMinutes() {
        BusinessException ex = assertThrows(BusinessException.class, () -> ticketSlaService.createPolicy(admin(), TicketSlaPolicyCommandDTO.builder().policyName("非法 SLA").firstResponseMinutes(60).resolveMinutes(30).build()));
        assertEquals("INVALID_TICKET_SLA_POLICY", ex.getCode());
        verify(policyRepository, never()).insert(any());
    }

    @Test
    @DisplayName("SLA实例: 工单命中策略后创建实例")
    void createOrRefreshInstanceShouldInsertWhenMissing() {
        Ticket ticket = ticket();
        when(policyRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(policy());
        when(instanceRepository.findByTicketId(TICKET_ID)).thenReturn(null);

        ticketSlaService.createOrRefreshInstance(ticket);

        ArgumentCaptor<TicketSlaInstance> captor = ArgumentCaptor.forClass(TicketSlaInstance.class);
        verify(instanceRepository).insert(captor.capture());
        assertEquals(TICKET_ID, captor.getValue().getTicketId());
        assertEquals(ticket.getCreatedAt().plusMinutes(30), captor.getValue().getFirstResponseDeadline());
    }

    @Test
    @DisplayName("SLA实例: 没有命中策略时不创建实例")
    void createOrRefreshInstanceShouldSkipWhenNoPolicy() {
        when(policyRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(null);
        ticketSlaService.createOrRefreshInstance(ticket());
        verify(instanceRepository, never()).insert(any());
    }

    @Test
    @DisplayName("SLA扫描: ADMIN 可以标记违约并触发升级")
    void scanBreachedInstancesShouldMarkCandidates() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 20, 12, 0);
        TicketSlaInstance first = TicketSlaInstance.builder().id(1L).ticketId(101L).firstResponseDeadline(now.minusMinutes(30)).resolveDeadline(now.plusMinutes(60)).breached(0).build();
        TicketSlaInstance second = TicketSlaInstance.builder().id(2L).ticketId(102L).firstResponseDeadline(now.minusMinutes(40)).resolveDeadline(now.minusMinutes(5)).breached(0).build();
        when(instanceRepository.findBreachedCandidates(now, 100)).thenReturn(List.of(first, second));
        when(ticketRepository.findUsersByRoleCode("ADMIN")).thenReturn(List.of(SysUser.builder().id(9L).status(1).build()));
        when(ticketRepository.findById(101L)).thenReturn(
                Ticket.builder().id(101L).creatorId(1L).status(TicketStatusEnum.PENDING_ASSIGN).priority(TicketPriorityEnum.HIGH).build(),
                Ticket.builder().id(101L).creatorId(1L).status(TicketStatusEnum.PROCESSING).priority(TicketPriorityEnum.URGENT).assigneeId(9L).build()
        );
        when(ticketRepository.findById(102L)).thenReturn(
                Ticket.builder().id(102L).creatorId(2L).status(TicketStatusEnum.PROCESSING).priority(TicketPriorityEnum.HIGH).assigneeId(3L).build(),
                Ticket.builder().id(102L).creatorId(2L).status(TicketStatusEnum.PROCESSING).priority(TicketPriorityEnum.URGENT).assigneeId(3L).build()
        );
        when(instanceRepository.markBreached(1L)).thenReturn(1);
        when(instanceRepository.markBreached(2L)).thenReturn(1);
        when(ticketRepository.updatePriority(101L, TicketPriorityEnum.URGENT)).thenReturn(1);
        when(ticketRepository.updatePriority(102L, TicketPriorityEnum.URGENT)).thenReturn(1);
        when(ticketRepository.updateAssigneeAndStatus(101L, 9L, TicketStatusEnum.PENDING_ASSIGN, TicketStatusEnum.PROCESSING)).thenReturn(1);

        TicketSlaScanResultDTO result = ticketSlaService.scanBreachedInstances(admin(), now, 100);

        assertEquals(2, result.getMarkedCount());
        assertEquals(1, result.getFirstResponseBreachedCount());
        assertEquals(1, result.getResolveBreachedCount());
        assertEquals(2, result.getEscalatedCount());
        assertEquals(2, result.getNotifiedCount());
        assertEquals(List.of(1L, 2L), result.getBreachedInstanceIds());
        verify(notificationService, times(2)).notifyBreached(any(), any(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("SLA扫描: 非 ADMIN 不允许执行")
    void scanBreachedInstancesShouldRejectNonAdmin() {
        BusinessException ex = assertThrows(BusinessException.class, () -> ticketSlaService.scanBreachedInstances(CurrentUser.builder().userId(1L).username("staff").roles(List.of("STAFF")).build(), LocalDateTime.of(2026, 4, 20, 12, 0), 100));
        assertEquals("ADMIN_REQUIRED", ex.getCode());
        verify(instanceRepository, never()).findBreachedCandidates(any(), anyInt());
    }

    private TicketSlaPolicy policy() {
        return TicketSlaPolicy.builder().id(POLICY_ID).policyName("高优先级系统问题").category("SYSTEM").priority("HIGH").firstResponseMinutes(30).resolveMinutes(240).enabled(1).build();
    }

    private Ticket ticket() {
        return Ticket.builder().id(TICKET_ID).category(TicketCategoryEnum.SYSTEM).priority(TicketPriorityEnum.HIGH).createdAt(LocalDateTime.of(2026, 4, 20, 9, 0)).build();
    }

    private CurrentUser admin() {
        return CurrentUser.builder().userId(9L).username("admin").roles(List.of("ADMIN")).build();
    }
}