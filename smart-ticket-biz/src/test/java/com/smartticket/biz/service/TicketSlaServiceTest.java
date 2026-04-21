package com.smartticket.biz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.TicketSlaPolicyCommandDTO;
import com.smartticket.biz.dto.TicketSlaScanResultDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketSlaInstanceRepository;
import com.smartticket.biz.repository.TicketSlaPolicyRepository;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketSlaInstance;
import com.smartticket.domain.entity.TicketSlaPolicy;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SLA 策略和实例服务测试。
 */
@ExtendWith(MockitoExtension.class)
class TicketSlaServiceTest {
    private static final Long POLICY_ID = 10L;
    private static final Long TICKET_ID = 100L;

    @Mock
    private TicketSlaPolicyRepository policyRepository;
    @Mock
    private TicketSlaInstanceRepository instanceRepository;

    private TicketSlaService ticketSlaService;

    @BeforeEach
    void setUp() {
        ticketSlaService = new TicketSlaService(
                policyRepository,
                instanceRepository,
                new TicketPermissionService()
        );
    }

    @Test
    @DisplayName("SLA 策略：ADMIN 可以创建合法策略")
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
        ArgumentCaptor<TicketSlaPolicy> policyCaptor = ArgumentCaptor.forClass(TicketSlaPolicy.class);
        verify(policyRepository).insert(policyCaptor.capture());
        assertEquals("SYSTEM", policyCaptor.getValue().getCategory());
        assertEquals("HIGH", policyCaptor.getValue().getPriority());
        assertEquals(30, policyCaptor.getValue().getFirstResponseMinutes());
        assertEquals(240, policyCaptor.getValue().getResolveMinutes());
    }

    @Test
    @DisplayName("SLA 策略：解决时限不能小于首次响应时限")
    void createPolicyShouldRejectInvalidMinutes() {
        BusinessException ex = assertThrows(BusinessException.class, () -> ticketSlaService.createPolicy(admin(),
                TicketSlaPolicyCommandDTO.builder()
                        .policyName("非法 SLA")
                        .firstResponseMinutes(60)
                        .resolveMinutes(30)
                        .build()));

        assertEquals("INVALID_TICKET_SLA_POLICY", ex.getCode());
        verify(policyRepository, never()).insert(any());
    }

    @Test
    @DisplayName("SLA 实例：工单命中策略后创建实例")
    void createOrRefreshInstanceShouldInsertWhenMissing() {
        Ticket ticket = ticket();
        when(policyRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(policy());
        when(instanceRepository.findByTicketId(TICKET_ID)).thenReturn(null);

        ticketSlaService.createOrRefreshInstance(ticket);

        ArgumentCaptor<TicketSlaInstance> instanceCaptor = ArgumentCaptor.forClass(TicketSlaInstance.class);
        verify(instanceRepository).insert(instanceCaptor.capture());
        TicketSlaInstance instance = instanceCaptor.getValue();
        assertEquals(TICKET_ID, instance.getTicketId());
        assertEquals(POLICY_ID, instance.getPolicyId());
        assertEquals(ticket.getCreatedAt().plusMinutes(30), instance.getFirstResponseDeadline());
        assertEquals(ticket.getCreatedAt().plusMinutes(240), instance.getResolveDeadline());
        assertEquals(0, instance.getBreached());
    }

    @Test
    @DisplayName("SLA 实例：没有命中策略时不创建实例")
    void createOrRefreshInstanceShouldSkipWhenNoPolicy() {
        when(policyRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(null);

        ticketSlaService.createOrRefreshInstance(ticket());

        verify(instanceRepository, never()).insert(any());
        verify(instanceRepository, never()).updateByTicketId(any());
    }

    @Test
    @DisplayName("SLA 违约扫描：ADMIN 可以标记已超过解决截止时间的实例")
    void scanBreachedInstancesShouldMarkCandidates() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 20, 12, 0);
        TicketSlaInstance first = TicketSlaInstance.builder()
                .id(1L)
                .ticketId(101L)
                .resolveDeadline(now.minusMinutes(10))
                .breached(0)
                .build();
        TicketSlaInstance second = TicketSlaInstance.builder()
                .id(2L)
                .ticketId(102L)
                .resolveDeadline(now.minusMinutes(5))
                .breached(0)
                .build();
        when(instanceRepository.findBreachedCandidates(now, 100)).thenReturn(List.of(first, second));
        when(instanceRepository.markBreached(1L)).thenReturn(1);
        when(instanceRepository.markBreached(2L)).thenReturn(0);

        TicketSlaScanResultDTO result = ticketSlaService.scanBreachedInstances(admin(), now, 100);

        assertEquals(now, result.getScanTime());
        assertEquals(100, result.getLimit());
        assertEquals(2, result.getCandidateCount());
        assertEquals(1, result.getMarkedCount());
        assertEquals(List.of(1L), result.getBreachedInstanceIds());
    }

    @Test
    @DisplayName("SLA 违约扫描：非 ADMIN 不允许执行")
    void scanBreachedInstancesShouldRejectNonAdmin() {
        BusinessException ex = assertThrows(BusinessException.class, () -> ticketSlaService.scanBreachedInstances(
                CurrentUser.builder().userId(1L).username("staff").roles(List.of("STAFF")).build(),
                LocalDateTime.of(2026, 4, 20, 12, 0),
                100
        ));

        assertEquals("ADMIN_REQUIRED", ex.getCode());
        verify(instanceRepository, never()).findBreachedCandidates(any(), anyInt());
        verify(instanceRepository, never()).markBreached(any());
    }

    private TicketSlaPolicy policy() {
        return TicketSlaPolicy.builder()
                .id(POLICY_ID)
                .policyName("高优先级系统问题")
                .category("SYSTEM")
                .priority("HIGH")
                .firstResponseMinutes(30)
                .resolveMinutes(240)
                .enabled(1)
                .build();
    }

    private Ticket ticket() {
        return Ticket.builder()
                .id(TICKET_ID)
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .createdAt(LocalDateTime.of(2026, 4, 20, 9, 0))
                .build();
    }

    private CurrentUser admin() {
        return CurrentUser.builder()
                .userId(9L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .build();
    }
}
