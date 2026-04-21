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
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketAssignmentRuleRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketAssignmentRule;
import com.smartticket.domain.entity.TicketQueue;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 自动分派规则和 preview 服务测试。
 */
@ExtendWith(MockitoExtension.class)
class TicketAssignmentRuleServiceTest {
    private static final Long RULE_ID = 20L;
    private static final Long TICKET_ID = 100L;
    private static final Long GROUP_ID = 30L;
    private static final Long QUEUE_ID = 40L;
    private static final Long STAFF_ID = 2L;

    @Mock
    private TicketAssignmentRuleRepository ruleRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private TicketGroupService ticketGroupService;
    @Mock
    private TicketQueueService ticketQueueService;
    @Mock
    private TicketRepository ticketRepository;

    private TicketAssignmentRuleService assignmentRuleService;

    @BeforeEach
    void setUp() {
        assignmentRuleService = new TicketAssignmentRuleService(
                ruleRepository,
                ticketService,
                ticketGroupService,
                ticketQueueService,
                ticketRepository,
                new TicketPermissionService()
        );
    }

    @Test
    @DisplayName("自动分派规则：ADMIN 可以创建合法规则")
    void createShouldAllowAdmin() {
        when(ticketQueueService.requireEnabled(QUEUE_ID)).thenReturn(TicketQueue.builder()
                .id(QUEUE_ID)
                .groupId(GROUP_ID)
                .enabled(1)
                .build());
        mockStaff(STAFF_ID);
        when(ruleRepository.insert(any(TicketAssignmentRule.class))).thenAnswer(invocation -> {
            TicketAssignmentRule rule = invocation.getArgument(0);
            rule.setId(RULE_ID);
            return 1;
        });
        when(ruleRepository.findById(RULE_ID)).thenReturn(rule());

        TicketAssignmentRule result = assignmentRuleService.create(admin(), command());

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
    @DisplayName("自动分派规则：至少需要一个目标")
    void createShouldRejectMissingTarget() {
        BusinessException ex = assertThrows(BusinessException.class, () -> assignmentRuleService.create(admin(),
                TicketAssignmentRuleCommandDTO.builder()
                        .ruleName("非法规则")
                        .category(TicketCategoryEnum.SYSTEM)
                        .priority(TicketPriorityEnum.HIGH)
                        .build()));

        assertEquals("INVALID_TICKET_ASSIGNMENT_RULE", ex.getCode());
        verify(ruleRepository, never()).insert(any());
    }

    @Test
    @DisplayName("自动分派 preview：命中规则时返回推荐目标")
    void previewShouldReturnMatchedRule() {
        when(ticketService.getDetail(user(), TICKET_ID)).thenReturn(TicketDetailDTO.builder()
                .ticket(ticket())
                .comments(List.of())
                .operationLogs(List.of())
                .build());
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(rule());

        TicketAssignmentPreviewDTO result = assignmentRuleService.preview(user(), TICKET_ID);

        assertTrue(result.isMatched());
        assertEquals(RULE_ID, result.getRuleId());
        assertEquals(GROUP_ID, result.getTargetGroupId());
        assertEquals(QUEUE_ID, result.getTargetQueueId());
        assertEquals(STAFF_ID, result.getTargetUserId());
    }

    @Test
    @DisplayName("自动分派 preview：未命中规则时返回 matched=false")
    void previewShouldReturnUnmatchedWhenNoRule() {
        when(ticketService.getDetail(user(), TICKET_ID)).thenReturn(TicketDetailDTO.builder()
                .ticket(ticket())
                .comments(List.of())
                .operationLogs(List.of())
                .build());
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(null);

        TicketAssignmentPreviewDTO result = assignmentRuleService.preview(user(), TICKET_ID);

        assertFalse(result.isMatched());
        assertEquals(TICKET_ID, result.getTicketId());
        assertEquals("未匹配到启用的自动分派规则", result.getReason());
    }

    @Test
    @DisplayName("真实自动分派：命中目标处理人后委托 TicketService.assignTicket")
    void autoAssignShouldDelegateToTicketService() {
        Ticket assigned = ticket();
        assigned.setAssigneeId(STAFF_ID);
        assigned.setStatus(TicketStatusEnum.PROCESSING);
        when(ticketService.getDetail(admin(), TICKET_ID)).thenReturn(TicketDetailDTO.builder()
                .ticket(ticket())
                .comments(List.of())
                .operationLogs(List.of())
                .build());
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(rule());
        when(ticketService.assignTicket(admin(), TICKET_ID, STAFF_ID)).thenReturn(assigned);

        Ticket result = assignmentRuleService.autoAssign(admin(), TICKET_ID);

        assertEquals(STAFF_ID, result.getAssigneeId());
        assertEquals(TicketStatusEnum.PROCESSING, result.getStatus());
        verify(ticketService).bindTicketQueue(admin(), TICKET_ID, GROUP_ID, QUEUE_ID);
        verify(ticketService).assignTicket(admin(), TICKET_ID, STAFF_ID);
    }

    @Test
    @DisplayName("真实自动分派：未命中规则时拒绝执行")
    void autoAssignShouldRejectWhenNoRuleMatched() {
        when(ticketService.getDetail(admin(), TICKET_ID)).thenReturn(TicketDetailDTO.builder()
                .ticket(ticket())
                .comments(List.of())
                .operationLogs(List.of())
                .build());
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assignmentRuleService.autoAssign(admin(), TICKET_ID));

        assertEquals("TICKET_ASSIGNMENT_RULE_NOT_MATCHED", ex.getCode());
        verify(ticketService, never()).assignTicket(any(), any(), any());
    }

    @Test
    @DisplayName("真实自动分派：规则没有目标处理人时拒绝执行")
    void autoAssignShouldRejectRuleWithoutTargetUser() {
        TicketAssignmentRule rule = rule();
        rule.setTargetUserId(null);
        when(ticketService.getDetail(admin(), TICKET_ID)).thenReturn(TicketDetailDTO.builder()
                .ticket(ticket())
                .comments(List.of())
                .operationLogs(List.of())
                .build());
        when(ruleRepository.findBestMatch("SYSTEM", "HIGH")).thenReturn(rule);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> assignmentRuleService.autoAssign(admin(), TICKET_ID));

        assertEquals("INVALID_TICKET_ASSIGNMENT_RULE", ex.getCode());
        verify(ticketService, never()).assignTicket(any(), any(), any());
    }

    private TicketAssignmentRuleCommandDTO command() {
        return TicketAssignmentRuleCommandDTO.builder()
                .ruleName("系统高优先级规则")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .targetGroupId(GROUP_ID)
                .targetQueueId(QUEUE_ID)
                .targetUserId(STAFF_ID)
                .weight(100)
                .enabled(true)
                .build();
    }

    private TicketAssignmentRule rule() {
        return TicketAssignmentRule.builder()
                .id(RULE_ID)
                .ruleName("系统高优先级规则")
                .category("SYSTEM")
                .priority("HIGH")
                .targetGroupId(GROUP_ID)
                .targetQueueId(QUEUE_ID)
                .targetUserId(STAFF_ID)
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

    private void mockStaff(Long userId) {
        when(ticketRepository.findUserById(userId)).thenReturn(SysUser.builder()
                .id(userId)
                .status(1)
                .build());
        when(ticketRepository.findRolesByUserId(userId)).thenReturn(List.of(SysRole.builder()
                .roleCode("STAFF")
                .build()));
    }

    private CurrentUser admin() {
        return CurrentUser.builder()
                .userId(9L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .build();
    }

    private CurrentUser user() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user")
                .roles(List.of("USER"))
                .build();
    }
}
