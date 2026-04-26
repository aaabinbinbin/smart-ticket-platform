package com.smartticket.biz.service.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.BeforeEach;
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

    @Mock
    private TicketCreateEnrichmentService ticketCreateEnrichmentService;

    @InjectMocks
    private TicketCommandService service;

    @BeforeEach
    void setUp() {
        when(ticketCreateEnrichmentService.enrich(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

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
        when(support.generateTicketNo(any(TicketTypeEnum.class))).thenReturn("INC202604231200001234");
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

    @Test
    void createTicketShouldReleaseIdempotencyLockWhenDoCreateFails() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境登录失败")
                .description("登录时报 500")
                .idempotencyKey("create-fail")
                .build();
        when(ticketIdempotencyService.normalize("create-fail")).thenReturn("create-fail");
        when(ticketIdempotencyService.enabled("create-fail")).thenReturn(true);
        when(ticketIdempotencyService.getCreatedTicketId(1L, "create-fail")).thenReturn(null);
        when(ticketIdempotencyService.acquireCreateLock(1L, "create-fail")).thenReturn(true);
        // doCreateTicket 中生成工单编号时抛出异常，触发 catch 释放幂等锁
        when(support.generateTicketNo(any(TicketTypeEnum.class))).thenThrow(new RuntimeException("DB write failure"));

        assertThrows(RuntimeException.class, () -> service.createTicket(operator(), command));

        verify(ticketIdempotencyService).releaseCreateLock(1L, "create-fail");
    }

    @Test
    void createTicketShouldBypassIdempotencyWhenKeyIsBlank() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境登录失败")
                .description("登录时报 500")
                .typeProfile(Map.of("env", "test"))
                .build();
        when(ticketIdempotencyService.normalize(null)).thenReturn(null);
        when(ticketIdempotencyService.enabled(null)).thenReturn(false);
        when(support.generateTicketNo(any(TicketTypeEnum.class))).thenReturn("INC202604251200001234");
        doAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(101L);
            return 1;
        }).when(ticketRepository).insert(any(Ticket.class));
        Ticket persisted = Ticket.builder()
                .id(101L)
                .ticketNo("INC202604251200001234")
                .title("测试环境登录失败")
                .description("登录时报 500")
                .type(TicketTypeEnum.INCIDENT)
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.MEDIUM)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(1L)
                .source("MANUAL")
                .build();
        when(support.requireTicket(101L)).thenReturn(persisted);
        when(support.snapshot(any(Ticket.class))).thenReturn("snapshot");

        Ticket result = service.createTicket(operator(), command);

        assertEquals(101L, result.getId());
        assertNull(result.getIdempotencyKey());
        verify(ticketIdempotencyService, never()).acquireCreateLock(any(), any());
        verify(ticketIdempotencyService, never()).getCreatedTicketId(any(), any());
        verify(ticketRepository).insert(any(Ticket.class));
    }

    @Test
    void sameIdempotencyKeyWithDifferentUserShouldNotConflict() {
        TicketCreateCommandDTO command = TicketCreateCommandDTO.builder()
                .title("测试环境登录失败")
                .description("登录时报 500")
                .idempotencyKey("create-1")
                .typeProfile(Map.of("env", "test"))
                .build();
        CurrentUser user1 = CurrentUser.builder().userId(1L).username("user1").roles(List.of("USER")).build();
        CurrentUser user2 = CurrentUser.builder().userId(2L).username("user2").roles(List.of("USER")).build();
        when(ticketIdempotencyService.normalize("create-1")).thenReturn("create-1");
        when(ticketIdempotencyService.enabled("create-1")).thenReturn(true);

        // user1 已有同一幂等键的结果，应复用
        when(ticketIdempotencyService.getCreatedTicketId(1L, "create-1")).thenReturn(101L);
        Ticket existing = Ticket.builder().id(101L).title("测试环境登录失败").status(TicketStatusEnum.PENDING_ASSIGN).build();
        when(support.requireTicket(101L)).thenReturn(existing);

        // user2 使用相同 key 但 userId 不同，应创建新工单
        when(ticketIdempotencyService.getCreatedTicketId(2L, "create-1")).thenReturn(null);
        when(ticketIdempotencyService.acquireCreateLock(2L, "create-1")).thenReturn(true);
        when(support.generateTicketNo(any(TicketTypeEnum.class))).thenReturn("INC202604251200001235");
        doAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(102L);
            return 1;
        }).when(ticketRepository).insert(any(Ticket.class));
        Ticket newTicket = Ticket.builder()
                .id(102L)
                .ticketNo("INC202604251200001235")
                .title("测试环境登录失败")
                .description("登录时报 500")
                .type(TicketTypeEnum.INCIDENT)
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.MEDIUM)
                .status(TicketStatusEnum.PENDING_ASSIGN)
                .creatorId(2L)
                .source("MANUAL")
                .idempotencyKey("create-1")
                .build();
        when(support.requireTicket(102L)).thenReturn(newTicket);
        when(support.snapshot(any(Ticket.class))).thenReturn("snapshot");

        // user1 复用已有工单，不触发 insert
        Ticket result1 = service.createTicket(user1, command);
        assertEquals(101L, result1.getId());
        verify(ticketIdempotencyService, never()).acquireCreateLock(1L, "create-1");

        // user2 创建新工单
        Ticket result2 = service.createTicket(user2, command);
        assertEquals(102L, result2.getId());
        assertEquals(2L, result2.getCreatorId().longValue());
        // user2 的 insert 是唯一一次入库写入
        verify(ticketRepository).insert(any(Ticket.class));
        verify(ticketIdempotencyService).acquireCreateLock(2L, "create-1");
        verify(ticketTypeProfileService).attachProfile(existing);
    }

    private CurrentUser operator() {
        return CurrentUser.builder()
                .userId(1L)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }
}
