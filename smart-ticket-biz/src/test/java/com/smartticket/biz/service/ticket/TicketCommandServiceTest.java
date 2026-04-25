package com.smartticket.biz.service.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.ticket.TicketRepository;
import com.smartticket.biz.service.sla.TicketSlaService;
import com.smartticket.biz.service.type.TicketTypeProfileService;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.OperationTypeEnum;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketCommandServiceTest {

    @Mock
    private TicketServiceSupport support;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketIdempotencyService ticketIdempotencyService;

    @Mock
    private TicketSlaService ticketSlaService;

    @Mock
    private TicketTypeProfileService ticketTypeProfileService;

    @InjectMocks
    private TicketCommandService service;

    @Test
    void createTicketShouldApplyDefaultTypeCategoryAndPriority() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境登录失败")
                .description("登录时报 500，影响研发自测")
                .typeProfile(Map.of("env", "test"))
                .idempotencyKey("create-1")
                .build();
        when(ticketIdempotencyService.normalize("create-1")).thenReturn("create-1");
        when(ticketIdempotencyService.enabled("create-1")).thenReturn(false);
        when(support.generateTicketNo()).thenReturn("INC202604231200001234");
        doAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(101L);
            return 1;
        }).when(ticketRepository).insert(any(Ticket.class));
        Ticket persisted = Ticket.builder()
                .id(101L)
                .ticketNo("INC202604231200001234")
                .title("测试环境登录失败")
                .description("登录时报 500，影响研发自测")
                .type(TicketTypeEnum.INCIDENT)
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.MEDIUM)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .source("MANUAL")
                .idempotencyKey("create-1")
                .build();
        when(support.requireTicket(101L)).thenReturn(persisted);
        when(support.snapshot(any(Ticket.class))).thenReturn("snapshot");

        Ticket result = service.createTicket(operator(), command);

        assertEquals(TicketTypeEnum.INCIDENT, result.getType());
        assertEquals(TicketCategoryEnum.SYSTEM, result.getCategory());
        assertEquals(TicketPriorityEnum.MEDIUM, result.getPriority());
        verify(ticketTypeProfileService).validate(TicketTypeEnum.INCIDENT, Map.of("env", "test"));
        verify(ticketTypeProfileService).saveOrUpdate(101L, TicketTypeEnum.INCIDENT, Map.of("env", "test"));
        verify(ticketSlaService).createOrRefreshInstance(persisted);
        verify(support).writeLog(eq(101L), eq(1L), eq(OperationTypeEnum.CREATE), eq("创建工单"), eq(null), eq("snapshot"));
    }

    @Test
    void createTicketShouldRejectAccessRequestWithoutRequiredKeywords() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("帮我处理一下")
                .description("尽快处理")
                .type(TicketTypeEnum.ACCESS_REQUEST)
                .build();
        when(ticketIdempotencyService.normalize(null)).thenReturn(null);
        when(ticketIdempotencyService.enabled(null)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createTicket(operator(), command));

        assertEquals("INVALID_TICKET_TYPE_REQUIREMENT", ex.getCode());
    }

    @Test
    void createTicketShouldReuseExistingIdempotentResultWithoutInsert() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境登录失败")
                .description("登录时报 500，影响研发自测")
                .idempotencyKey(" create-1 ")
                .build();
        Ticket existing = Ticket.builder()
                .id(101L)
                .title("测试环境登录失败")
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .build();
        when(ticketIdempotencyService.normalize(" create-1 ")).thenReturn("create-1");
        when(ticketIdempotencyService.enabled("create-1")).thenReturn(true);
        when(ticketIdempotencyService.getCreatedTicketId(1L, "create-1")).thenReturn(101L);
        when(support.requireTicket(101L)).thenReturn(existing);

        Ticket result = service.createTicket(operator(), command);

        assertEquals(101L, result.getId());
        assertEquals("create-1", command.getIdempotencyKey());
        verify(ticketRepository, never()).insert(any(Ticket.class));
        verify(ticketIdempotencyService, never()).acquireCreateLock(1L, "create-1");
        verify(ticketTypeProfileService).attachProfile(existing);
    }

    @Test
    void createTicketShouldRejectDuplicateIdempotentRequestWhenLockIsHeld() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境登录失败")
                .description("登录时报 500，影响研发自测")
                .idempotencyKey("create-1")
                .build();
        when(ticketIdempotencyService.normalize("create-1")).thenReturn("create-1");
        when(ticketIdempotencyService.enabled("create-1")).thenReturn(true);
        when(ticketIdempotencyService.getCreatedTicketId(1L, "create-1")).thenReturn(null);
        when(ticketIdempotencyService.acquireCreateLock(1L, "create-1")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createTicket(operator(), command));

        assertEquals("IDEMPOTENT_REQUEST_PROCESSING", ex.getCode());
        verify(ticketRepository, never()).insert(any(Ticket.class));
    }

    private CurrentUser operator() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
