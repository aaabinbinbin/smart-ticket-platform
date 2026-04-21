package com.smartticket.biz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.TicketAssignmentPreviewDTO;
import com.smartticket.biz.dto.TicketAssignmentRuleCommandDTO;
import com.smartticket.biz.dto.TicketAssignmentStatsDTO;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketAssignmentRuleRepository;
import com.smartticket.biz.repository.TicketOperationLogRepository;
import com.smartticket.biz.repository.TicketQueueRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.entity.TicketQueueMember;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketAssignmentRuleServiceTest {
    private static final Long RULE_ID = 20L;
    private static final Long TICKET_ID = 100L;
    private static final Long GROUP_ID = 30L;
    private static final Long QUEUE_ID = 40L;
    private static final Long QUEUE_ID_2 = 41L;
    private static final Long STAFF_ID = 2L;
    private static final Long STAFF_ID_2 = 3L;
    private static final Long OWNER_ID = 9L;
    private static final Long MEMBER_ID = 88L;

    @Mock private TicketAssignmentRuleRepository ruleRepository;
    @Mock private TicketQueryService ticketQueryService;
    @Mock private TicketWorkflowService ticketWorkflowService;
    @Mock private TicketQueueBindingService ticketQueueBindingService;
    @Mock private TicketGroupService ticketGroupService;
    @Mock private TicketQueueService ticketQueueService;
    @Mock private TicketQueueRepository ticketQueueRepository;
    @Mock private TicketQueueMemberService ticketQueueMemberService;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketOperationLogRepository ticketOperationLogRepository;

    private TicketAssignmentRuleService assignmentRuleService;

    @BeforeEach
    void setUp() {
        assignmentRuleService = new TicketAssignmentRuleService(
                ruleRepository,
                ticketQueryService,
                ticketWorkflowService,
                ticketQueueBindingService,
                ticketGroupService,
                ticketQueueService,
                ticketQueueRepository,
                ticketQueueMemberService,
                ticketRepository,
                ticketOperationLogRepository,
                new TicketPermissionService()
        );
    }

    @Test
    @DisplayName("ADMIN can create a valid assignment rule")
    void createShouldAllowAdmin() {
        when(ticketQueueService.requireEnabled(QUEUE_ID)).thenReturn(queue(QUEUE_ID));
        mockStaff(STAFF_ID);
        when(ruleRepository.insert(any(TicketAssignmentRule.class))).thenAnswer(invocation -> {
            TicketAssignmentRule rule = invocation.getArgument(0);
            rule.setId(RULE_ID);
            return 1;
        });
        when(ruleRepository.findById(RULE_ID)).thenReturn(ruleWithExplicitUser());

        TicketAssignmentRule result = assignmentRuleService.create(admin(), commandWithExplicitUser());

        assertEquals(RULE_ID, result.getId());
        ArgumentCaptor<TicketAssignmentRule> ruleCaptor = ArgumentCaptor.forClass(TicketAssignmentRule.class);
        verify(ruleRepository).insert(ruleCaptor.capture());
        assertEquals("SYSTEM", ruleCaptor.getValue().getCategory());
        assertEquals("HIGH", ruleCaptor.getValue().getPriority());
        assertEquals(GROUP_ID, ruleCaptor.getValue().getTargetGroupId());
        assertEquals(QUEUE_ID, ruleCaptor.getValue().getTargetQueueId());
        assertEquals(STAFF_ID, ruleCaptor.getValue().getTargetUserId());
    }

    @Test
    @DisplayName("create rejects missing target")
    void createShouldRejectMissingTarget() {
        BusinessException ex = assertThrows(BusinessException.class, () -> assignmentRuleService.create(admin(),
                TicketAssignmentRuleCommandDTO.builder()
                        .ruleName("invalid")
                        .category(TicketCategoryEnum.SYSTEM)
                        .priority(TicketPriorityEnum.HIGH)
                        .build()));

        assertEquals("INVALID_TICKET_ASSIGNMENT_RULE", ex.getCode());
        verify(ruleRepository, never()).insert(any());
    }

    @Test
    @DisplayName("preview returns matched rule")
    void previewShouldReturnMatchedRule() {
        when(ticketQueryService.getDetail(user(), TICKET_ID)).thenReturn(ticketDetail(ticket()));
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(ruleWithExplicitUser());

        TicketAssignmentPreviewDTO result = assignmentRuleService.preview(user(), TICKET_ID);

        assertTrue(result.isMatched());
        assertEquals(RULE_ID, result.getRuleId());
        assertEquals(GROUP_ID, result.getTargetGroupId());
        assertEquals(QUEUE_ID, result.getTargetQueueId());
        assertEquals(STAFF_ID, result.getTargetUserId());
    }

    @Test
    @DisplayName("preview returns unmatched when no rule exists")
    void previewShouldReturnUnmatchedWhenNoRule() {
        when(ticketQueryService.getDetail(user(), TICKET_ID)).thenReturn(ticketDetail(ticket()));
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(null);

        TicketAssignmentPreviewDTO result = assignmentRuleService.preview(user(), TICKET_ID);

        assertFalse(result.isMatched());
        assertEquals(TICKET_ID, result.getTicketId());
        assertEquals("未匹配到启用的自动分派规则", result.getReason());
    }

    @Test
    @DisplayName("auto assign still supports explicit target user")
    void autoAssignShouldDelegateToWorkflowForExplicitUser() {
        Ticket assigned = ticket();
        assigned.setAssigneeId(STAFF_ID);
        assigned.setStatus(TicketStatusEnum.PROCESSING);
        when(ticketQueryService.getDetail(admin(), TICKET_ID)).thenReturn(ticketDetail(ticket()));
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(ruleWithExplicitUser());
        when(ticketWorkflowService.assignTicket(admin(), TICKET_ID, STAFF_ID)).thenReturn(assigned);

        Ticket result = assignmentRuleService.autoAssign(admin(), TICKET_ID);

        assertEquals(STAFF_ID, result.getAssigneeId());
        verify(ticketQueueBindingService).bindTicketQueue(admin(), TICKET_ID, GROUP_ID, QUEUE_ID);
        verify(ticketWorkflowService).assignTicket(admin(), TICKET_ID, STAFF_ID);
        verify(ticketQueueMemberService, never()).markAssigned(any());
        verify(ticketOperationLogRepository).insert(any());
    }

    @Test
    @DisplayName("auto assign chooses the least loaded queue member")
    void autoAssignShouldSelectLeastLoadedQueueMember() {
        Ticket assigned = ticket();
        assigned.setAssigneeId(STAFF_ID_2);
        assigned.setStatus(TicketStatusEnum.PROCESSING);
        when(ticketQueryService.getDetail(admin(), TICKET_ID)).thenReturn(ticketDetail(ticket()));
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(ruleWithQueueOnly());
        when(ticketQueueService.requireEnabled(QUEUE_ID)).thenReturn(queue(QUEUE_ID));
        when(ticketQueueMemberService.listEnabledMembers(QUEUE_ID)).thenReturn(List.of(
                member(MEMBER_ID, QUEUE_ID, STAFF_ID),
                member(MEMBER_ID + 1, QUEUE_ID, STAFF_ID_2)
        ));
        when(ticketRepository.countOpenAssignedTickets(STAFF_ID)).thenReturn(5L);
        when(ticketRepository.countOpenAssignedTickets(STAFF_ID_2)).thenReturn(1L);
        when(ticketWorkflowService.assignTicket(admin(), TICKET_ID, STAFF_ID_2)).thenReturn(assigned);

        Ticket result = assignmentRuleService.autoAssign(admin(), TICKET_ID);

        assertEquals(STAFF_ID_2, result.getAssigneeId());
        verify(ticketQueueBindingService).bindTicketQueue(admin(), TICKET_ID, GROUP_ID, QUEUE_ID);
        verify(ticketQueueMemberService).markAssigned(MEMBER_ID + 1);
        verify(ticketWorkflowService).assignTicket(admin(), TICKET_ID, STAFF_ID_2);
    }

    @Test
    @DisplayName("auto assign selects member across queues when only group is configured")
    void autoAssignShouldSelectMemberAcrossGroupQueues() {
        Ticket assigned = ticket();
        assigned.setAssigneeId(STAFF_ID_2);
        assigned.setStatus(TicketStatusEnum.PROCESSING);
        when(ticketQueryService.getDetail(admin(), TICKET_ID)).thenReturn(ticketDetail(ticket()));
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(ruleWithGroupOnly());
        when(ticketGroupService.requireEnabled(GROUP_ID)).thenReturn(TicketGroup.builder().id(GROUP_ID).enabled(1).build());
        when(ticketQueueRepository.findEnabledByGroupId(GROUP_ID)).thenReturn(List.of(queue(QUEUE_ID), queue(QUEUE_ID_2)));
        when(ticketQueueMemberService.listEnabledMembers(QUEUE_ID)).thenReturn(List.of(member(MEMBER_ID, QUEUE_ID, STAFF_ID)));
        when(ticketQueueMemberService.listEnabledMembers(QUEUE_ID_2)).thenReturn(List.of(member(MEMBER_ID + 1, QUEUE_ID_2, STAFF_ID_2)));
        when(ticketRepository.countOpenAssignedTickets(STAFF_ID)).thenReturn(3L);
        when(ticketRepository.countOpenAssignedTickets(STAFF_ID_2)).thenReturn(0L);
        when(ticketWorkflowService.assignTicket(admin(), TICKET_ID, STAFF_ID_2)).thenReturn(assigned);

        Ticket result = assignmentRuleService.autoAssign(admin(), TICKET_ID);

        assertEquals(STAFF_ID_2, result.getAssigneeId());
        verify(ticketQueueBindingService).bindTicketQueue(admin(), TICKET_ID, GROUP_ID, QUEUE_ID_2);
        verify(ticketQueueMemberService).markAssigned(MEMBER_ID + 1);
    }

    @Test
    @DisplayName("auto assign falls back to group owner when queue has no active members")
    void autoAssignShouldFallbackToGroupOwner() {
        Ticket assigned = ticket();
        assigned.setAssigneeId(OWNER_ID);
        assigned.setStatus(TicketStatusEnum.PROCESSING);
        when(ticketQueryService.getDetail(admin(), TICKET_ID)).thenReturn(ticketDetail(ticket()));
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(ruleWithQueueOnly());
        when(ticketQueueService.requireEnabled(QUEUE_ID)).thenReturn(queue(QUEUE_ID));
        when(ticketQueueMemberService.listEnabledMembers(QUEUE_ID)).thenReturn(List.of());
        when(ticketGroupService.requireEnabled(GROUP_ID)).thenReturn(TicketGroup.builder().id(GROUP_ID).ownerUserId(OWNER_ID).enabled(1).build());
        mockStaff(OWNER_ID);
        when(ticketWorkflowService.assignTicket(admin(), TICKET_ID, OWNER_ID)).thenReturn(assigned);

        Ticket result = assignmentRuleService.autoAssign(admin(), TICKET_ID);

        assertEquals(OWNER_ID, result.getAssigneeId());
        verify(ticketQueueBindingService).bindTicketQueue(admin(), TICKET_ID, GROUP_ID, QUEUE_ID);
        verify(ticketWorkflowService).assignTicket(admin(), TICKET_ID, OWNER_ID);
    }

    @Test
    @DisplayName("auto assign binds queue and keeps pending when nobody is available")
    void autoAssignShouldKeepPendingWhenNobodyAvailable() {
        Ticket pending = ticket();
        pending.setGroupId(GROUP_ID);
        pending.setQueueId(QUEUE_ID);
        when(ticketQueryService.getDetail(admin(), TICKET_ID)).thenReturn(ticketDetail(ticket()), ticketDetail(pending));
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(ruleWithQueueOnly());
        when(ticketQueueService.requireEnabled(QUEUE_ID)).thenReturn(queue(QUEUE_ID));
        when(ticketQueueMemberService.listEnabledMembers(QUEUE_ID)).thenReturn(List.of());
        when(ticketGroupService.requireEnabled(GROUP_ID)).thenReturn(TicketGroup.builder().id(GROUP_ID).enabled(1).build());

        Ticket result = assignmentRuleService.autoAssign(admin(), TICKET_ID);

        assertEquals(TicketStatusEnum.PENDING_ASSIGN, result.getStatus());
        assertEquals(GROUP_ID, result.getGroupId());
        assertEquals(QUEUE_ID, result.getQueueId());
        verify(ticketQueueBindingService).bindTicketQueue(admin(), TICKET_ID, GROUP_ID, QUEUE_ID);
        verify(ticketWorkflowService, never()).assignTicket(any(), any(), any());
    }

    @Test
    @DisplayName("stats aggregates auto assign and claim counters")
    void statsShouldAggregateCounts() {
        when(ticketOperationLogRepository.countByOperationType(OperationTypeEnum.AUTO_ASSIGN_MATCHED.getCode())).thenReturn(6L);
        when(ticketOperationLogRepository.countByOperationType(OperationTypeEnum.AUTO_ASSIGN_FALLBACK.getCode())).thenReturn(2L);
        when(ticketOperationLogRepository.countByOperationType(OperationTypeEnum.AUTO_ASSIGN_PENDING.getCode())).thenReturn(2L);
        when(ticketOperationLogRepository.countByOperationType(OperationTypeEnum.CLAIM.getCode())).thenReturn(3L);

        TicketAssignmentStatsDTO result = assignmentRuleService.stats();

        assertEquals(10L, result.getTotalAutoAssignCount());
        assertEquals(8L, result.getAutoAssignedCount());
        assertEquals(3L, result.getClaimedCount());
        assertEquals(new BigDecimal("0.8000"), result.getAutoAssignHitRate());
    }

    @Test
    @DisplayName("auto assign rejects when no rule matches")
    void autoAssignShouldRejectWhenNoRuleMatched() {
        when(ticketQueryService.getDetail(admin(), TICKET_ID)).thenReturn(ticketDetail(ticket()));
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assignmentRuleService.autoAssign(admin(), TICKET_ID));

        assertEquals("TICKET_ASSIGNMENT_RULE_NOT_MATCHED", ex.getCode());
        verify(ticketWorkflowService, never()).assignTicket(any(), any(), any());
    }

    private TicketAssignmentRuleCommandDTO commandWithExplicitUser() {
        return TicketAssignmentRuleCommandDTO.builder()
                .ruleName("system high priority")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .targetGroupId(GROUP_ID)
                .targetQueueId(QUEUE_ID)
                .targetUserId(STAFF_ID)
                .weight(100)
                .enabled(true)
                .build();
    }

    private TicketAssignmentRule ruleWithExplicitUser() {
        return TicketAssignmentRule.builder()
                .id(RULE_ID)
                .ruleName("system high priority")
                .category("SYSTEM")
                .priority("HIGH")
                .targetGroupId(GROUP_ID)
                .targetQueueId(QUEUE_ID)
                .targetUserId(STAFF_ID)
                .weight(100)
                .enabled(1)
                .build();
    }

    private TicketAssignmentRule ruleWithQueueOnly() {
        return TicketAssignmentRule.builder()
                .id(RULE_ID)
                .ruleName("queue load balancing")
                .category("SYSTEM")
                .priority("HIGH")
                .targetGroupId(GROUP_ID)
                .targetQueueId(QUEUE_ID)
                .weight(100)
                .enabled(1)
                .build();
    }

    private TicketAssignmentRule ruleWithGroupOnly() {
        return TicketAssignmentRule.builder()
                .id(RULE_ID)
                .ruleName("group load balancing")
                .category("SYSTEM")
                .priority("HIGH")
                .targetGroupId(GROUP_ID)
                .weight(100)
                .enabled(1)
                .build();
    }

    private Ticket ticket() {
        return Ticket.builder()
                .id(TICKET_ID)
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .build();
    }

    private TicketDetailDTO ticketDetail(Ticket ticket) {
        return TicketDetailDTO.builder().ticket(ticket).comments(List.of()).operationLogs(List.of()).build();
    }

    private TicketQueue queue(Long queueId) {
        return TicketQueue.builder().id(queueId).groupId(GROUP_ID).enabled(1).build();
    }

    private TicketQueueMember member(Long memberId, Long queueId, Long userId) {
        return TicketQueueMember.builder().id(memberId).queueId(queueId).userId(userId).enabled(1).build();
    }

    private void mockStaff(Long userId) {
        when(ticketRepository.findUserById(userId)).thenReturn(SysUser.builder().id(userId).status(1).build());
        when(ticketRepository.findRolesByUserId(userId)).thenReturn(List.of(SysRole.builder().roleCode("STAFF").build()));
    }

    private CurrentUser admin() {
        return CurrentUser.builder().userId(99L).username("admin").roles(List.of("ADMIN")).build();
    }

    private CurrentUser user() {
        return CurrentUser.builder().userId(1L).username("user").roles(List.of("USER")).build();
    }
}
