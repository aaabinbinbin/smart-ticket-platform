package com.smartticket.biz.service.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.assignment.TicketAssignmentRuleCommandDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentStatsDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.assignment.TicketAssignmentRuleRepository;
import com.smartticket.biz.repository.ticket.TicketOperationLogRepository;
import com.smartticket.biz.service.ticket.TicketPermissionService;
import com.smartticket.biz.service.ticket.TicketQueryService;
import com.smartticket.biz.service.ticket.TicketQueueBindingService;
import com.smartticket.biz.service.ticket.TicketUserDirectoryService;
import com.smartticket.biz.service.ticket.TicketWorkflowService;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketAssignmentRuleServiceTest {

    @Mock
    private TicketAssignmentRuleRepository ruleRepository;

    @Mock
    private TicketQueryService ticketQueryService;

    @Mock
    private TicketWorkflowService ticketWorkflowService;

    @Mock
    private TicketQueueBindingService ticketQueueBindingService;

    @Mock
    private TicketGroupService ticketGroupService;

    @Mock
    private TicketQueueService ticketQueueService;

    @Mock
    private TicketAssignmentTargetResolver ticketAssignmentTargetResolver;

    @Mock
    private TicketQueueMemberService ticketQueueMemberService;

    @Mock
    private TicketOperationLogRepository ticketOperationLogRepository;

    @Mock
    private TicketPermissionService permissionService;

    @Mock
    private TicketUserDirectoryService ticketUserDirectoryService;

    @InjectMocks
    private TicketAssignmentRuleService service;

    @Test
    void createShouldRejectQueueOutsideTargetGroup() {
        TicketAssignmentRuleCommandDTO command = TicketAssignmentRuleCommandDTO.builder()
                .ruleName("system-high")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .targetGroupId(10L)
                .targetQueueId(20L)
                .enabled(true)
                .build();
        when(ticketQueueService.requireEnabled(20L)).thenReturn(TicketQueue.builder()
                .id(20L)
                .groupId(99L)
                .build());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(adminUser(), command));

        assertEquals("INVALID_TICKET_ASSIGNMENT_RULE", ex.getCode());
    }

    @Test
    void statsShouldCalculateHitRate() {
        when(ticketOperationLogRepository.countByOperationType("AUTO_ASSIGN_MATCHED")).thenReturn(3L);
        when(ticketOperationLogRepository.countByOperationType("AUTO_ASSIGN_FALLBACK")).thenReturn(1L);
        when(ticketOperationLogRepository.countByOperationType("AUTO_ASSIGN_PENDING")).thenReturn(1L);
        when(ticketOperationLogRepository.countByOperationType("CLAIM")).thenReturn(2L);

        TicketAssignmentStatsDTO stats = service.stats();

        assertEquals(5L, stats.getTotalAutoAssignCount());
        assertEquals(4L, stats.getAutoAssignedCount());
        assertEquals(new BigDecimal("0.8000"), stats.getAutoAssignHitRate());
        assertEquals(2L, stats.getClaimedCount());
    }

    private CurrentUser adminUser() {
        return CurrentUser.builder()
                .userId(1L)
                .username("admin1")
                .roles(List.of("ADMIN"))
                .build();
    }
}
