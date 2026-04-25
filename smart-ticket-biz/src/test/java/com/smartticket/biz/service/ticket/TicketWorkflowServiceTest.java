package com.smartticket.biz.service.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.ticket.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.approval.TicketApprovalService;
import com.smartticket.biz.service.assignment.TicketGroupService;
import com.smartticket.biz.service.assignment.TicketQueueMemberService;
import com.smartticket.biz.service.knowledge.TicketKnowledgeBuildTaskService;
import com.smartticket.biz.service.sla.TicketSlaService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketKnowledgeBuildTask;
import com.smartticket.domain.enums.OperationTypeEnum;
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

    @Mock
    private TicketKnowledgeBuildTaskService knowledgeBuildTaskService;

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

    @Test
    void claimTicketShouldFailWhenConcurrentClaimAlreadyChangedState() {
        Ticket ticket = Ticket.builder()
                .id(1003L)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .queueId(20L)
                .build();
        when(support.requireTicket(1003L)).thenReturn(ticket);
        when(ticketQueueMemberService.isEnabledMember(20L, 2L)).thenReturn(true);
        when(ticketRepository.updateAssigneeAndStatus(1003L, 2L, TicketStatusEnum.PENDING_ASSIGN, TicketStatusEnum.PROCESSING))
                .thenReturn(0);
        doThrow(new BusinessException(com.smartticket.common.exception.BusinessErrorCode.TICKET_STATE_CHANGED))
                .when(support).requireUpdated(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.claimTicket(staffUser(), 1003L));

        assertEquals("TICKET_STATE_CHANGED", ex.getCode());
        verify(ticketApprovalService).requireApprovalPassed(ticket);
    }

    @Test
    void updateStatusShouldAllowProcessingToResolvedWhenAssigneeExists() {
        Ticket before = Ticket.builder()
                .id(1004L)
                .status(TicketStatusEnum.PROCESSING)
                .creatorId(1L)
                .assigneeId(2L)
                .solutionSummary("已恢复")
                .build();
        Ticket after = Ticket.builder()
                .id(1004L)
                .status(TicketStatusEnum.RESOLVED)
                .creatorId(1L)
                .assigneeId(2L)
                .solutionSummary("已恢复")
                .build();
        when(support.requireTicket(1004L)).thenReturn(before, after);
        when(support.permissionService()).thenReturn(new TicketPermissionService());
        when(ticketRepository.updateStatus(1004L, TicketStatusEnum.PROCESSING, TicketStatusEnum.RESOLVED, "已恢复"))
                .thenReturn(1);
        when(support.snapshot(before)).thenReturn("before");
        when(support.snapshot(after)).thenReturn("after");

        Ticket result = service.updateStatus(
                staffUser(),
                1004L,
                TicketUpdateStatusCommandDTO.builder()
                        .targetStatus(TicketStatusEnum.RESOLVED)
                        .solutionSummary("已恢复")
                        .build()
        );

        assertEquals(TicketStatusEnum.RESOLVED, result.getStatus());
        verify(support).writeLog(1004L, 2L, OperationTypeEnum.UPDATE_STATUS, "更新工单状态", "before", "after");
        verify(ticketDetailCacheService).evict(1004L);
    }

    @Test
    void closeTicketShouldCreateKnowledgeBuildTaskAndPublishEvent() {
        Ticket before = Ticket.builder()
                .id(1005L)
                .status(TicketStatusEnum.RESOLVED)
                .creatorId(1L)
                .assigneeId(2L)
                .solutionSummary("重启认证服务后恢复")
                .build();
        Ticket after = Ticket.builder()
                .id(1005L)
                .status(TicketStatusEnum.CLOSED)
                .creatorId(1L)
                .assigneeId(2L)
                .solutionSummary("重启认证服务后恢复")
                .build();
        when(support.requireTicket(1005L)).thenReturn(before, after);
        when(support.permissionService()).thenReturn(new TicketPermissionService());
        when(ticketRepository.updateStatus(1005L, TicketStatusEnum.RESOLVED, TicketStatusEnum.CLOSED, "重启认证服务后恢复"))
                .thenReturn(1);
        when(support.snapshot(before)).thenReturn("before");
        when(support.snapshot(after)).thenReturn("after");
        when(knowledgeBuildTaskService.createPending(1005L)).thenReturn(TicketKnowledgeBuildTask.builder()
                .id(9001L)
                .ticketId(1005L)
                .build());

        Ticket result = service.closeTicket(operator(), 1005L);

        assertEquals(TicketStatusEnum.CLOSED, result.getStatus());
        verify(knowledgeBuildTaskService).createPending(1005L);
        verify(support).publishTicketClosedAfterCommit(1005L, 9001L);
        verify(support).writeLog(1005L, 1L, OperationTypeEnum.CLOSE, "关闭工单", "before", "after");
    }

    @Test
    void closeTicketShouldRejectNonResolvedTicket() {
        Ticket before = Ticket.builder()
                .id(1006L)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .build();
        when(support.requireTicket(1006L)).thenReturn(before);
        when(support.permissionService()).thenReturn(new TicketPermissionService());
        // requireResolved 校验不通过：工单状态不是 RESOLVED
        doThrow(new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, "只有已解决工单可以关闭"))
                .when(support).requireStatus(before, TicketStatusEnum.RESOLVED, "只有已解决工单可以关闭");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.closeTicket(operator(), 1006L));

        assertEquals("INVALID_TICKET_STATUS", ex.getCode());
    }

    @Test
    void updateStatusShouldRejectInvalidTransition() {
        Ticket ticket = Ticket.builder()
                .id(1007L)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .build();
        when(support.requireTicket(1007L)).thenReturn(ticket);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateStatus(
                adminUser(),
                1007L,
                TicketUpdateStatusCommandDTO.builder().targetStatus(TicketStatusEnum.RESOLVED).build()
        ));

        // PENDING_ASSIGN -> RESOLVED 跳过 PROCESSING，非法流转
        assertEquals("INVALID_TICKET_STATUS_TRANSITION", ex.getCode());
    }

    @Test
    void claimTicketShouldRejectProcessingTicket() {
        Ticket ticket = Ticket.builder()
                .id(1008L)
                .status(TicketStatusEnum.PROCESSING)
                .build();
        when(support.requireTicket(1008L)).thenReturn(ticket);
        // requirePendingAssign 校验不通过：工单不是 PENDING_ASSIGN
        doThrow(new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, "只有待分配工单可以认领"))
                .when(support).requireStatus(ticket, TicketStatusEnum.PENDING_ASSIGN, "只有待分配工单可以认领");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.claimTicket(staffUser(), 1008L));

        assertEquals("INVALID_TICKET_STATUS", ex.getCode());
    }

    @Test
    void claimTicketShouldRejectNonQueueMember() {
        Ticket ticket = Ticket.builder()
                .id(1009L)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .queueId(20L)
                .build();
        when(support.requireTicket(1009L)).thenReturn(ticket);
        // 非队列成员且非分组负责人
        when(ticketQueueMemberService.isEnabledMember(20L, 2L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.claimTicket(staffUser(), 1009L));

        assertEquals("TICKET_CLAIM_FORBIDDEN", ex.getCode());
    }

    @Test
    void closedTicketShouldRejectAllOperations() {
        Ticket ticket = Ticket.builder()
                .id(1010L)
                .status(TicketStatusEnum.CLOSED)
                .creatorId(1L)
                .build();
        when(support.requireTicket(1010L)).thenReturn(ticket);

        // 关闭工单不能再次关闭
        doThrow(new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, "只有已解决工单可以关闭"))
                .when(support).requireStatus(ticket, TicketStatusEnum.RESOLVED, "只有已解决工单可以关闭");
        when(support.permissionService()).thenReturn(new TicketPermissionService());

        BusinessException closeEx = assertThrows(BusinessException.class, () -> service.closeTicket(operator(), 1010L));
        assertEquals("INVALID_TICKET_STATUS", closeEx.getCode());

        // 关闭工单不能认领
        doThrow(new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, "只有待分配工单可以认领"))
                .when(support).requireStatus(ticket, TicketStatusEnum.PENDING_ASSIGN, "只有待分配工单可以认领");

        BusinessException claimEx = assertThrows(BusinessException.class, () -> service.claimTicket(staffUser(), 1010L));
        assertEquals("INVALID_TICKET_STATUS", claimEx.getCode());

        // 关闭工单不能更新状态（非法流转）
        BusinessException updateEx = assertThrows(BusinessException.class, () -> service.updateStatus(
                adminUser(),
                1010L,
                TicketUpdateStatusCommandDTO.builder().targetStatus(TicketStatusEnum.PROCESSING).build()
        ));
        assertEquals("INVALID_TICKET_STATUS_TRANSITION", updateEx.getCode());
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

    private CurrentUser staffUser() {
        return CurrentUser.builder()
                .userId(2L)
                .username("staff1")
                .roles(List.of("STAFF"))
                .build();
    }
}
