package com.smartticket.biz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.dto.TicketDetailDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.dto.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.repository.TicketCommentRepository;
import com.smartticket.biz.repository.TicketOperationLogRepository;
import com.smartticket.biz.repository.TicketRepository;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.SysRole;
import com.smartticket.domain.entity.SysUser;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketComment;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.enums.OperationTypeEnum;
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

/**
 * 工单核心业务服务测试。
 *
 * <p>这些测试只验证阶段四的业务规则，不连接真实数据库。
 * Repository 使用 Mockito 模拟，避免测试依赖本地 MySQL 数据。</p>
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    private static final Long CREATOR_ID = 1L;
    private static final Long STAFF_ID = 2L;
    private static final Long OTHER_STAFF_ID = 3L;
    private static final Long TICKET_ID = 100L;

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketCommentRepository ticketCommentRepository;
    @Mock
    private TicketOperationLogRepository operationLogRepository;
    @Mock
    private TicketDetailCacheService ticketDetailCacheService;
    @Mock
    private TicketIdempotencyService ticketIdempotencyService;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        TicketPermissionService permissionService = new TicketPermissionService();
        ticketService = new TicketService(
                ticketRepository,
                ticketCommentRepository,
                operationLogRepository,
                permissionService,
                ticketDetailCacheService,
                ticketIdempotencyService
        );
    }

    @Test
    @DisplayName("创建工单：初始状态为 PENDING_ASSIGN，并写入 CREATE 操作日志")
    void createTicketShouldCreatePendingTicketAndWriteLog() {
        printScenario("创建工单", user(), "新工单应进入 PENDING_ASSIGN，并记录 CREATE 日志");
        final Ticket[] insertedTicket = new Ticket[1];
        when(ticketRepository.insert(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(TICKET_ID);
            insertedTicket[0] = ticket;
            printTicket("模拟入库工单", ticket);
            return 1;
        });
        when(ticketRepository.findById(TICKET_ID)).thenAnswer(invocation -> insertedTicket[0]);

        Ticket result = ticketService.createTicket(user(), TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .idempotencyKey("idem-001")
                .build());

        assertEquals(TicketStatusEnum.PENDING_ASSIGN, result.getStatus());
        printTicket("创建结果", result);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).insert(ticketCaptor.capture());
        Ticket inserted = ticketCaptor.getValue();
        assertEquals(CREATOR_ID, inserted.getCreatorId());
        assertEquals("MANUAL", inserted.getSource());
        assertEquals(TicketStatusEnum.PENDING_ASSIGN, inserted.getStatus());
        assertNotNull(inserted.getTicketNo());

        ArgumentCaptor<TicketOperationLog> logCaptor = ArgumentCaptor.forClass(TicketOperationLog.class);
        verify(operationLogRepository).insert(logCaptor.capture());
        assertEquals(OperationTypeEnum.CREATE, logCaptor.getValue().getOperationType());
        assertEquals(CREATOR_ID, logCaptor.getValue().getOperatorId());
        assertEquals(TICKET_ID, logCaptor.getValue().getTicketId());
        printLog("操作日志", logCaptor.getValue());
    }

    @Test
    @DisplayName("查询详情：Redis 缓存命中后使用缓存内工单做权限判断，不查数据库")
    void getDetailShouldReturnCachedDetailWithoutDatabaseQuery() {
        Ticket current = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        TicketDetailDTO cached = TicketDetailDTO.builder()
                .ticket(current)
                .comments(List.of())
                .operationLogs(List.of())
                .build();
        printScenario("详情缓存", staff(), "缓存命中后使用 CurrentUser + cached.ticket 做内存权限判断");
        when(ticketDetailCacheService.get(TICKET_ID)).thenReturn(cached);

        TicketDetailDTO result = ticketService.getDetail(staff(), TICKET_ID);

        assertEquals(cached, result);
        verify(ticketRepository, never()).findVisibleById(any(), any());
        verify(ticketRepository, never()).findById(any());
        verify(ticketCommentRepository, never()).findByTicketId(any());
        verify(operationLogRepository, never()).findByTicketId(any());
    }

    @Test
    @DisplayName("查询详情：Redis 缓存命中但当前用户不可见时拒绝返回")
    void getDetailShouldRejectInvisibleCachedTicket() {
        Ticket current = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        TicketDetailDTO cached = TicketDetailDTO.builder()
                .ticket(current)
                .comments(List.of())
                .operationLogs(List.of())
                .build();
        printScenario("详情缓存权限", otherUser(), "缓存命中也不能绕过工单可见性权限");
        when(ticketDetailCacheService.get(TICKET_ID)).thenReturn(cached);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.getDetail(otherUser(), TICKET_ID)
        );

        assertEquals("TICKET_NOT_FOUND", ex.getCode());
        verify(ticketRepository, never()).findVisibleById(any(), any());
        verify(ticketRepository, never()).findById(any());
        verify(ticketCommentRepository, never()).findByTicketId(any());
        verify(operationLogRepository, never()).findByTicketId(any());
    }

    @Test
    @DisplayName("创建工单：幂等键命中时返回已创建工单，不重复入库")
    void createTicketShouldReturnExistingTicketWhenIdempotencyKeyExists() {
        Ticket existing = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        printScenario("创建幂等", user(), "相同用户和相同幂等键再次提交时返回第一次创建的工单");
        when(ticketIdempotencyService.enabled("idem-001")).thenReturn(true);
        when(ticketIdempotencyService.normalize("idem-001")).thenReturn("idem-001");
        when(ticketIdempotencyService.getCreatedTicketId(CREATOR_ID, "idem-001")).thenReturn(TICKET_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(existing);

        Ticket result = ticketService.createTicket(user(), TicketCreateCommandDTO.builder()
                .title("测试环境无法登录")
                .description("登录时报 500")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .idempotencyKey("idem-001")
                .build());

        assertEquals(TICKET_ID, result.getId());
        verify(ticketRepository, never()).insert(any());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("分配工单：管理员可将待分配工单分配给 STAFF，并流转到 PROCESSING")
    void assignTicketShouldMovePendingTicketToProcessing() {
        Ticket before = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        Ticket after = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        printScenario("分配工单", admin(), "PENDING_ASSIGN -> PROCESSING，目标处理人必须是 STAFF");
        printTicketFlow(before, after);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before, after);
        mockEnabledStaff(STAFF_ID);
        when(ticketRepository.updateAssigneeAndStatus(
                TICKET_ID,
                STAFF_ID,
                TicketStatusEnum.PENDING_ASSIGN,
                TicketStatusEnum.PROCESSING
        )).thenReturn(1);

        Ticket result = ticketService.assignTicket(admin(), TICKET_ID, STAFF_ID);

        assertEquals(TicketStatusEnum.PROCESSING, result.getStatus());
        assertEquals(STAFF_ID, result.getAssigneeId());
        verify(ticketRepository).updateAssigneeAndStatus(
                TICKET_ID,
                STAFF_ID,
                TicketStatusEnum.PENDING_ASSIGN,
                TicketStatusEnum.PROCESSING
        );
        verifyLog(OperationTypeEnum.ASSIGN, 9L);
    }

    @Test
    @DisplayName("分配工单：状态被并发修改时应拒绝继续写日志")
    void assignTicketShouldRejectWhenStateChangedConcurrently() {
        Ticket before = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        printScenario("分配工单并发失败", admin(), "更新时状态条件不匹配，说明工单已被其他请求修改");
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before);
        mockEnabledStaff(STAFF_ID);
        when(ticketRepository.updateAssigneeAndStatus(
                TICKET_ID,
                STAFF_ID,
                TicketStatusEnum.PENDING_ASSIGN,
                TicketStatusEnum.PROCESSING
        )).thenReturn(0);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(admin(), TICKET_ID, STAFF_ID)
        );

        assertEquals("TICKET_STATE_CHANGED", ex.getCode());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("分配工单：普通用户不能执行管理员分配动作")
    void assignTicketShouldRejectNonAdmin() {
        printScenario("分配工单失败", user(), "普通 USER 尝试执行管理员分配动作，应被拒绝");
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.assignTicket(user(), TICKET_ID, STAFF_ID)
        );

        assertEquals("ADMIN_REQUIRED", ex.getCode());
        printException(ex);
        verify(ticketRepository, never()).updateAssigneeAndStatus(any(), any(), any(), any());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("转派工单：当前负责人可转派处理中的工单，状态保持 PROCESSING")
    void transferTicketShouldAllowCurrentAssignee() {
        Ticket before = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        Ticket after = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, OTHER_STAFF_ID);
        printScenario("转派工单", staff(), "当前负责人把 PROCESSING 工单转派给另一个 STAFF，状态不变");
        printTicketFlow(before, after);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before, after);
        mockEnabledStaff(OTHER_STAFF_ID);
        when(ticketRepository.updateAssignee(TICKET_ID, OTHER_STAFF_ID, TicketStatusEnum.PROCESSING)).thenReturn(1);

        Ticket result = ticketService.transferTicket(staff(), TICKET_ID, OTHER_STAFF_ID);

        assertEquals(TicketStatusEnum.PROCESSING, result.getStatus());
        assertEquals(OTHER_STAFF_ID, result.getAssigneeId());
        verify(ticketRepository).updateAssignee(TICKET_ID, OTHER_STAFF_ID, TicketStatusEnum.PROCESSING);
        verifyLog(OperationTypeEnum.TRANSFER, STAFF_ID);
    }

    @Test
    @DisplayName("转派工单：非当前负责人且非管理员不能转派")
    void transferTicketShouldRejectUnrelatedUser() {
        Ticket current = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        printScenario("转派工单失败", otherUser(), "非当前负责人且非管理员尝试转派，应被拒绝");
        printTicket("当前工单", current);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(current);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.transferTicket(otherUser(), TICKET_ID, OTHER_STAFF_ID)
        );

        assertEquals("TICKET_TRANSFER_FORBIDDEN", ex.getCode());
        printException(ex);
        verify(ticketRepository, never()).updateAssignee(any(), any(), any());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("更新状态：当前负责人可将 PROCESSING 流转为 RESOLVED")
    void updateStatusShouldAllowAssigneeResolveProcessingTicket() {
        Ticket before = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        Ticket after = ticket(TicketStatusEnum.RESOLVED, CREATOR_ID, STAFF_ID);
        after.setSolutionSummary("重启服务后恢复");
        printScenario("解决工单", staff(), "当前负责人将 PROCESSING -> RESOLVED，并写入解决摘要");
        printTicketFlow(before, after);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before, after);
        when(ticketRepository.updateStatus(
                TICKET_ID,
                TicketStatusEnum.PROCESSING,
                TicketStatusEnum.RESOLVED,
                "重启服务后恢复"
        )).thenReturn(1);

        Ticket result = ticketService.updateStatus(staff(), TICKET_ID, TicketUpdateStatusCommandDTO.builder()
                .targetStatus(TicketStatusEnum.RESOLVED)
                .solutionSummary("重启服务后恢复")
                .build());

        assertEquals(TicketStatusEnum.RESOLVED, result.getStatus());
        assertEquals("重启服务后恢复", result.getSolutionSummary());
        verify(ticketRepository).updateStatus(
                TICKET_ID,
                TicketStatusEnum.PROCESSING,
                TicketStatusEnum.RESOLVED,
                "重启服务后恢复"
        );
        verifyLog(OperationTypeEnum.UPDATE_STATUS, STAFF_ID);
    }

    @Test
    @DisplayName("更新状态：禁止跳过状态链路")
    void updateStatusShouldRejectInvalidTransition() {
        Ticket current = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        printScenario("非法状态流转", admin(), "尝试 PENDING_ASSIGN -> RESOLVED，违反状态机约束");
        printTicket("当前工单", current);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(current);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.updateStatus(admin(), TICKET_ID, TicketUpdateStatusCommandDTO.builder()
                        .targetStatus(TicketStatusEnum.RESOLVED)
                        .build())
        );

        assertEquals("INVALID_TICKET_STATUS_TRANSITION", ex.getCode());
        printException(ex);
        verify(ticketRepository, never()).updateStatus(any(), any(), any(), any());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("更新状态：关闭工单必须走 close 接口，不能走通用状态接口")
    void updateStatusShouldRejectCloseTargetStatus() {
        Ticket current = ticket(TicketStatusEnum.RESOLVED, CREATOR_ID, STAFF_ID);
        printScenario("关闭入口错误", user(), "通用状态接口不再承担关闭业务语义");
        when(ticketRepository.findById(TICKET_ID)).thenReturn(current);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.updateStatus(user(), TICKET_ID, TicketUpdateStatusCommandDTO.builder()
                        .targetStatus(TicketStatusEnum.CLOSED)
                        .build())
        );

        assertEquals("CLOSE_TICKET_USE_CLOSE_API", ex.getCode());
        verify(ticketRepository, never()).updateStatus(any(), any(), any(), any());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("添加评论：可见工单的用户可以评论未关闭工单，并写 COMMENT 日志")
    void addCommentShouldWriteCommentAndLog() {
        Ticket current = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        printScenario("添加评论", staff(), "当前负责人对 PROCESSING 工单添加处理评论");
        printTicket("当前工单", current);
        when(ticketRepository.findVisibleById(TICKET_ID, STAFF_ID)).thenReturn(current);
        when(ticketCommentRepository.insert(any(TicketComment.class))).thenAnswer(invocation -> {
            TicketComment comment = invocation.getArgument(0);
            comment.setId(200L);
            System.out.printf("  评论入库: id=%s, ticketId=%s, commenterId=%s, content=%s%n",
                    comment.getId(), comment.getTicketId(), comment.getCommenterId(), comment.getContent());
            return 1;
        });
        when(ticketCommentRepository.findById(200L)).thenReturn(TicketComment.builder()
                .id(200L)
                .ticketId(TICKET_ID)
                .commenterId(STAFF_ID)
                .commentType("USER_REPLY")
                .content("正在排查日志")
                .createdAt(LocalDateTime.of(2026, 4, 18, 19, 30, 0))
                .build());

        TicketComment result = ticketService.addComment(staff(), TICKET_ID, "正在排查日志");

        assertEquals(200L, result.getId());
        assertEquals("USER_REPLY", result.getCommentType());
        assertEquals("正在排查日志", result.getContent());
        assertEquals(LocalDateTime.of(2026, 4, 18, 19, 30, 0), result.getCreatedAt());
        verifyLog(OperationTypeEnum.COMMENT, STAFF_ID);
    }

    @Test
    @DisplayName("添加评论：已关闭工单不能继续评论")
    void addCommentShouldRejectClosedTicket() {
        Ticket current = ticket(TicketStatusEnum.CLOSED, CREATOR_ID, STAFF_ID);
        printScenario("添加评论失败", staff(), "已关闭工单不能继续追加评论");
        printTicket("当前工单", current);
        when(ticketRepository.findVisibleById(TICKET_ID, STAFF_ID)).thenReturn(current);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.addComment(staff(), TICKET_ID, "还能处理吗")
        );

        assertEquals("TICKET_CLOSED", ex.getCode());
        printException(ex);
        verify(ticketCommentRepository, never()).insert(any());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("关闭工单：提单人可关闭已解决工单，并写 CLOSE 日志")
    void closeTicketShouldAllowCreatorCloseResolvedTicket() {
        Ticket before = ticket(TicketStatusEnum.RESOLVED, CREATOR_ID, STAFF_ID);
        before.setSolutionSummary("已修复");
        Ticket after = ticket(TicketStatusEnum.CLOSED, CREATOR_ID, STAFF_ID);
        after.setSolutionSummary("已修复");
        printScenario("关闭工单", user(), "提单人关闭 RESOLVED 工单，状态变为 CLOSED");
        printTicketFlow(before, after);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before, after);
        when(ticketRepository.updateStatus(
                TICKET_ID,
                TicketStatusEnum.RESOLVED,
                TicketStatusEnum.CLOSED,
                "已修复"
        )).thenReturn(1);

        Ticket result = ticketService.closeTicket(user(), TICKET_ID);

        assertEquals(TicketStatusEnum.CLOSED, result.getStatus());
        verify(ticketRepository).updateStatus(
                TICKET_ID,
                TicketStatusEnum.RESOLVED,
                TicketStatusEnum.CLOSED,
                "已修复"
        );
        verifyLog(OperationTypeEnum.CLOSE, CREATOR_ID);
    }

    @Test
    @DisplayName("分页查询：管理员走全量分页，普通用户走可见范围分页")
    void pageTicketsShouldUseDifferentScopeByRole() {
        TicketPageQueryDTO query = TicketPageQueryDTO.builder()
                .pageNo(1)
                .pageSize(10)
                .status(TicketStatusEnum.PROCESSING)
                .build();
        printScenario("分页查询", admin(), "管理员查全量范围，普通用户查自己可见范围");
        System.out.printf("  查询条件: pageNo=%s, pageSize=%s, status=%s%n",
                query.getPageNo(), query.getPageSize(), query.getStatus());
        when(ticketRepository.pageAll("PROCESSING", null, null, 0, 10)).thenReturn(List.of(ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID)));
        when(ticketRepository.countAll("PROCESSING", null, null)).thenReturn(1L);
        when(ticketRepository.pageVisible(CREATOR_ID, "PROCESSING", null, null, 0, 10)).thenReturn(List.of());
        when(ticketRepository.countVisible(CREATOR_ID, "PROCESSING", null, null)).thenReturn(0L);

        PageResult<Ticket> adminPage = ticketService.pageTickets(admin(), query);
        PageResult<Ticket> userPage = ticketService.pageTickets(user(), query);

        assertEquals(1L, adminPage.getTotal());
        assertEquals(0L, userPage.getTotal());
        System.out.printf("  管理员结果: total=%s, records=%s%n", adminPage.getTotal(), adminPage.getRecords().size());
        System.out.printf("  普通用户结果: total=%s, records=%s%n", userPage.getTotal(), userPage.getRecords().size());
        verify(ticketRepository).pageAll("PROCESSING", null, null, 0, 10);
        verify(ticketRepository).pageVisible(CREATOR_ID, "PROCESSING", null, null, 0, 10);
    }

    private void mockEnabledStaff(Long userId) {
        when(ticketRepository.findUserById(userId)).thenReturn(SysUser.builder()
                .id(userId)
                .status(1)
                .build());
        when(ticketRepository.findRolesByUserId(userId)).thenReturn(List.of(SysRole.builder()
                .roleCode("STAFF")
                .build()));
    }

    private void verifyLog(OperationTypeEnum operationType, Long operatorId) {
        ArgumentCaptor<TicketOperationLog> logCaptor = ArgumentCaptor.forClass(TicketOperationLog.class);
        verify(operationLogRepository).insert(logCaptor.capture());
        TicketOperationLog log = logCaptor.getValue();
        assertEquals(operationType, log.getOperationType());
        assertEquals(operatorId, log.getOperatorId());
        assertEquals(TICKET_ID, log.getTicketId());
        printLog("操作日志", log);
    }

    private void printScenario(String name, CurrentUser operator, String description) {
        System.out.printf("%n[测试场景] %s%n", name);
        System.out.printf("  说明: %s%n", description);
        System.out.printf("  操作人: userId=%s, username=%s, roles=%s%n",
                operator.getUserId(), operator.getUsername(), operator.getRoles());
    }

    private void printTicket(String label, Ticket ticket) {
        System.out.printf("  %s: id=%s, ticketNo=%s, status=%s, creatorId=%s, assigneeId=%s, solutionSummary=%s%n",
                label,
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getStatus(),
                ticket.getCreatorId(),
                ticket.getAssigneeId(),
                ticket.getSolutionSummary());
    }

    private void printTicketFlow(Ticket before, Ticket after) {
        printTicket("流转前", before);
        printTicket("流转后", after);
    }

    private void printLog(String label, TicketOperationLog log) {
        System.out.printf("  %s: ticketId=%s, operatorId=%s, operationType=%s, before=%s, after=%s%n",
                label,
                log.getTicketId(),
                log.getOperatorId(),
                log.getOperationType(),
                log.getBeforeValue(),
                log.getAfterValue());
    }

    private void printException(BusinessException ex) {
        System.out.printf("  业务异常: code=%s, message=%s%n", ex.getCode(), ex.getMessage());
    }

    private Ticket ticket(TicketStatusEnum status, Long creatorId, Long assigneeId) {
        return Ticket.builder()
                .id(TICKET_ID)
                .ticketNo("INC202604170001")
                .title("测试工单")
                .description("测试描述")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.MEDIUM)
                .status(status)
                .creatorId(creatorId)
                .assigneeId(assigneeId)
                .source("MANUAL")
                .build();
    }

    private CurrentUser user() {
        return CurrentUser.builder()
                .userId(CREATOR_ID)
                .username("user1")
                .roles(List.of("USER"))
                .build();
    }

    private CurrentUser staff() {
        return CurrentUser.builder()
                .userId(STAFF_ID)
                .username("staff1")
                .roles(List.of("USER", "STAFF"))
                .build();
    }

    private CurrentUser admin() {
        return CurrentUser.builder()
                .userId(9L)
                .username("admin1")
                .roles(List.of("USER", "STAFF", "ADMIN"))
                .build();
    }

    private CurrentUser otherUser() {
        return CurrentUser.builder()
                .userId(8L)
                .username("user2")
                .roles(List.of("USER"))
                .build();
    }
}
