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
import com.smartticket.domain.entity.TicketGroup;
import com.smartticket.domain.entity.TicketOperationLog;
import com.smartticket.domain.entity.TicketQueue;
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
import org.springframework.context.ApplicationEventPublisher;

/**
 * 瀹搞儱宕熼弽绋跨妇娑撴艾濮熼張宥呭濞村鐦妴? *
 * <p>鏉╂瑤绨哄ù瀣槸閸欘亪鐛欑拠渚€妯佸▓闈涙磽閻ㄥ嫪绗熼崝陇顫夐崚娆欑礉娑撳秷绻涢幒銉ф埂鐎圭偞鏆熼幑顔肩氨閵? * Repository 娴ｈ法鏁?Mockito 濡剝瀚欓敍宀勪缉閸忓秵绁寸拠鏇氱贩鐠ф牗婀伴崷?MySQL 閺佺増宓侀妴?/p>
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
    @Mock
    private TicketSlaService ticketSlaService;
    @Mock
    private TicketGroupService ticketGroupService;
    @Mock
    private TicketQueueService ticketQueueService;
    @Mock
    private TicketQueueMemberService ticketQueueMemberService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
                ticketIdempotencyService,
                ticketSlaService,
                ticketGroupService,
                ticketQueueService,
                ticketQueueMemberService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("閸掓稑缂撳銉ュ礋閿涙艾鍨垫慨瀣Ц閹椒璐?PENDING_ASSIGN閿涘苯鑻熼崘娆忓弳 CREATE 閹垮秳缍旈弮銉ョ箶")
    void createTicketShouldCreatePendingTicketAndWriteLog() {
        printScenario("閸掓稑缂撳銉ュ礋", user(), "閺傛澘浼愰崡鏇炵安鏉╂稑鍙?PENDING_ASSIGN閿涘苯鑻熺拋鏉跨秿 CREATE 閺冦儱绻?);
        final Ticket[] insertedTicket = new Ticket[1];
        when(ticketRepository.insert(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(TICKET_ID);
            insertedTicket[0] = ticket;
            printTicket("濡剝瀚欓崗銉ョ氨瀹搞儱宕?, ticket);
            return 1;
        });
        when(ticketRepository.findById(TICKET_ID)).thenAnswer(invocation -> insertedTicket[0]);

        Ticket result = ticketService.createTicket(user(), TicketCreateCommandDTO.builder()
                .title("濞村鐦悳顖氼暔閺冪姵纭堕惂璇茬秿")
                .description("閻ц缍嶉弮鑸靛Г 500")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .idempotencyKey("idem-001")
                .build());

        assertEquals(TicketStatusEnum.PENDING_ASSIGN, result.getStatus());
        printTicket("閸掓稑缂撶紒鎾寸亯", result);

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
        printLog("閹垮秳缍旈弮銉ョ箶", logCaptor.getValue());
    }

    @Test
    @DisplayName("閺屻儴顕楃拠锔藉剰閿涙瓓edis 缂傛挸鐡ㄩ崨鎴掕厬閸氬簼濞囬悽銊х处鐎涙ê鍞村銉ュ礋閸嬫碍娼堥梽鎰灲閺傤叏绱濇稉宥嗙叀閺佺増宓佹惔?)
    void getDetailShouldReturnCachedDetailWithoutDatabaseQuery() {
        Ticket current = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        TicketDetailDTO cached = TicketDetailDTO.builder()
                .ticket(current)
                .comments(List.of())
                .operationLogs(List.of())
                .build();
        printScenario("鐠囷附鍎忕紓鎾崇摠", staff(), "缂傛挸鐡ㄩ崨鎴掕厬閸氬簼濞囬悽?CurrentUser + cached.ticket 閸嬫艾鍞寸€涙ɑ娼堥梽鎰灲閺?);
        when(ticketDetailCacheService.get(TICKET_ID)).thenReturn(cached);

        TicketDetailDTO result = ticketService.getDetail(staff(), TICKET_ID);

        assertEquals(cached, result);
        verify(ticketRepository, never()).findVisibleById(any(), any());
        verify(ticketRepository, never()).findById(any());
        verify(ticketCommentRepository, never()).findByTicketId(any());
        verify(operationLogRepository, never()).findByTicketId(any());
    }

    @Test
    @DisplayName("閺屻儴顕楃拠锔藉剰閿涙瓓edis 缂傛挸鐡ㄩ崨鎴掕厬娴ｅ棗缍嬮崜宥囨暏閹磋渹绗夐崣顖濐潌閺冭埖瀚嗙紒婵婄箲閸?)
    void getDetailShouldRejectInvisibleCachedTicket() {
        Ticket current = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        TicketDetailDTO cached = TicketDetailDTO.builder()
                .ticket(current)
                .comments(List.of())
                .operationLogs(List.of())
                .build();
        printScenario("鐠囷附鍎忕紓鎾崇摠閺夊啴妾?, otherUser(), "缂傛挸鐡ㄩ崨鎴掕厬娑旂喍绗夐懗鐣岀搏鏉╁洤浼愰崡鏇炲讲鐟欎焦鈧勬綀闂?);
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
    @DisplayName("閸掓稑缂撳銉ュ礋閿涙艾绠撶粵澶愭暛閸涙垝鑵戦弮鎯扮箲閸ョ偛鍑￠崚娑樼紦瀹搞儱宕熼敍灞肩瑝闁插秴顦查崗銉ョ氨")
    void createTicketShouldReturnExistingTicketWhenIdempotencyKeyExists() {
        Ticket existing = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        printScenario("閸掓稑缂撻獮鍌滅搼", user(), "閻╃鎮撻悽銊﹀煕閸滃瞼娴夐崥灞界畵缁涘鏁崘宥嗩偧閹绘劒姘﹂弮鎯扮箲閸ョ偟顑囨稉鈧▎鈥冲灡瀵よ櫣娈戝銉ュ礋");
        when(ticketIdempotencyService.enabled("idem-001")).thenReturn(true);
        when(ticketIdempotencyService.normalize("idem-001")).thenReturn("idem-001");
        when(ticketIdempotencyService.getCreatedTicketId(CREATOR_ID, "idem-001")).thenReturn(TICKET_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(existing);

        Ticket result = ticketService.createTicket(user(), TicketCreateCommandDTO.builder()
                .title("濞村鐦悳顖氼暔閺冪姵纭堕惂璇茬秿")
                .description("閻ц缍嶉弮鑸靛Г 500")
                .category(TicketCategoryEnum.SYSTEM)
                .priority(TicketPriorityEnum.HIGH)
                .idempotencyKey("idem-001")
                .build());

        assertEquals(TICKET_ID, result.getId());
        verify(ticketRepository, never()).insert(any());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("閸掑棝鍘ゅ銉ュ礋閿涙氨顓搁悶鍡楁喅閸欘垰鐨㈠鍛瀻闁板秴浼愰崡鏇炲瀻闁板秶绮?STAFF閿涘苯鑻熷ù浣芥祮閸?PROCESSING")
    void assignTicketShouldMovePendingTicketToProcessing() {
        Ticket before = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        Ticket after = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        printScenario("閸掑棝鍘ゅ銉ュ礋", admin(), "PENDING_ASSIGN -> PROCESSING閿涘瞼娲伴弽鍥ь槱閻炲棔姹夎箛鍛淬€忛弰?STAFF");
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
    @DisplayName("閸掑棝鍘ゅ銉ュ礋閿涙氨濮搁幀浣筋潶楠炶泛褰傛穱顔芥暭閺冭泛绨查幏鎺旂卜缂佈呯敾閸愭瑦妫╄箛?)
    void assignTicketShouldRejectWhenStateChangedConcurrently() {
        Ticket before = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        printScenario("閸掑棝鍘ゅ銉ュ礋楠炶泛褰傛径杈Е", admin(), "閺囧瓨鏌婇弮鍓佸Ц閹焦娼禒鏈电瑝閸栧綊鍘ら敍宀冾嚛閺勫骸浼愰崡鏇炲嚒鐞氼偄鍙炬禒鏍嚞濮瑰倷鎱ㄩ弨?);
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
    @DisplayName("閸掑棝鍘ゅ銉ュ礋閿涙碍娅橀柅姘辨暏閹磋渹绗夐懗鑺ュ⒔鐞涘瞼顓搁悶鍡楁喅閸掑棝鍘ら崝銊ょ稊")
    void assignTicketShouldRejectNonAdmin() {
        printScenario("閸掑棝鍘ゅ銉ュ礋婢惰精瑙?, user(), "閺咁噣鈧?USER 鐏忔繆鐦幍褑顢戠粻锛勬倞閸涙ê鍨庨柊宥呭З娴ｆ粣绱濇惔鏃囶潶閹锋帞绮?);
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
    @DisplayName("缂佹垵鐣惧銉ュ礋闂冪喎鍨敍姘鳖吀閻炲棗鎲抽崣顖氱殺閺堫亜鍙ч梻顓炰紣閸楁洜绮︾€规艾鍩岄崥顖滄暏闂冪喎鍨?)
    void bindTicketQueueShouldUpdateQueueBinding() {
        Ticket before = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        Ticket after = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        after.setGroupId(30L);
        after.setQueueId(40L);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before, after);
        when(ticketQueueService.requireEnabled(40L)).thenReturn(TicketQueue.builder()
                .id(40L)
                .groupId(30L)
                .enabled(1)
                .build());
        when(ticketRepository.updateQueueBinding(TICKET_ID, 30L, 40L)).thenReturn(1);

        Ticket result = ticketService.bindTicketQueue(admin(), TICKET_ID, 30L, 40L);

        assertEquals(30L, result.getGroupId());
        assertEquals(40L, result.getQueueId());
        verify(ticketGroupService).requireEnabled(30L);
        verify(ticketRepository).updateQueueBinding(TICKET_ID, 30L, 40L);
        verifyLog(OperationTypeEnum.BIND_QUEUE, 9L);
    }

    @Test
    @DisplayName("鏉烆剚娣冲銉ュ礋閿涙艾缍嬮崜宥堢鐠愶絼姹夐崣顖濇祮濞叉儳顦╅悶鍡曡厬閻ㄥ嫬浼愰崡鏇礉閻樿埖鈧椒绻氶幐?PROCESSING")
    void transferTicketShouldAllowCurrentAssignee() {
        Ticket before = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        Ticket after = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, OTHER_STAFF_ID);
        printScenario("鏉烆剚娣冲銉ュ礋", staff(), "瑜版挸澧犵拹鐔荤煑娴滅儤濡?PROCESSING 瀹搞儱宕熸潪顒佹烦缂佹瑥褰熸稉鈧稉?STAFF閿涘瞼濮搁幀浣风瑝閸?);
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
    @DisplayName("鏉烆剚娣冲銉ュ礋閿涙岸娼ぐ鎾冲鐠愮喕鐭楁禍杞扮瑬闂堢偟顓搁悶鍡楁喅娑撳秷鍏樻潪顒佹烦")
    void transferTicketShouldRejectUnrelatedUser() {
        Ticket current = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        printScenario("鏉烆剚娣冲銉ュ礋婢惰精瑙?, otherUser(), "闂堢偛缍嬮崜宥堢鐠愶絼姹夋稉鏃堟姜缁狅紕鎮婇崨妯虹毦鐠囨洝娴嗗ú鎾呯礉鎼存棁顫﹂幏鎺旂卜");
        printTicket("瑜版挸澧犲銉ュ礋", current);
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
    @DisplayName("閺囧瓨鏌婇悩鑸碘偓渚婄窗瑜版挸澧犵拹鐔荤煑娴滃搫褰茬亸?PROCESSING 濞翠浇娴嗘稉?RESOLVED")
    void updateStatusShouldAllowAssigneeResolveProcessingTicket() {
        Ticket before = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        Ticket after = ticket(TicketStatusEnum.RESOLVED, CREATOR_ID, STAFF_ID);
        after.setSolutionSummary("闁插秴鎯庨張宥呭閸氬孩浠径?);
        printScenario("鐟欙絽鍠呭銉ュ礋", staff(), "瑜版挸澧犵拹鐔荤煑娴滃搫鐨?PROCESSING -> RESOLVED閿涘苯鑻熼崘娆忓弳鐟欙絽鍠呴幗妯款洣");
        printTicketFlow(before, after);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before, after);
        when(ticketRepository.updateStatus(
                TICKET_ID,
                TicketStatusEnum.PROCESSING,
                TicketStatusEnum.RESOLVED,
                "闁插秴鎯庨張宥呭閸氬孩浠径?
        )).thenReturn(1);

        Ticket result = ticketService.updateStatus(staff(), TICKET_ID, TicketUpdateStatusCommandDTO.builder()
                .targetStatus(TicketStatusEnum.RESOLVED)
                .solutionSummary("闁插秴鎯庨張宥呭閸氬孩浠径?)
                .build());

        assertEquals(TicketStatusEnum.RESOLVED, result.getStatus());
        assertEquals("闁插秴鎯庨張宥呭閸氬孩浠径?, result.getSolutionSummary());
        verify(ticketRepository).updateStatus(
                TICKET_ID,
                TicketStatusEnum.PROCESSING,
                TicketStatusEnum.RESOLVED,
                "闁插秴鎯庨張宥呭閸氬孩浠径?
        );
        verifyLog(OperationTypeEnum.UPDATE_STATUS, STAFF_ID);
    }

    @Test
    @DisplayName("閺囧瓨鏌婇悩鑸碘偓渚婄窗缁備焦顒涚捄瀹犵箖閻樿埖鈧線鎽肩捄?)
    void updateStatusShouldRejectInvalidTransition() {
        Ticket current = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        printScenario("闂堢偞纭堕悩鑸碘偓浣圭ウ鏉?, admin(), "鐏忔繆鐦?PENDING_ASSIGN -> RESOLVED閿涘矁绻氶崣宥囧Ц閹焦婧€缁撅附娼?);
        printTicket("瑜版挸澧犲銉ュ礋", current);
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
    @DisplayName("閺囧瓨鏌婇悩鑸碘偓渚婄窗閸忔娊妫村銉ュ礋韫囧懘銆忕挧?close 閹恒儱褰涢敍灞肩瑝閼冲€熻泲闁氨鏁ら悩鑸碘偓浣瑰复閸?)
    void updateStatusShouldRejectCloseTargetStatus() {
        Ticket current = ticket(TicketStatusEnum.RESOLVED, CREATOR_ID, STAFF_ID);
        printScenario("閸忔娊妫撮崗銉ュ經闁挎瑨顕?, user(), "闁氨鏁ら悩鑸碘偓浣瑰复閸欙絼绗夐崘宥嗗閹峰懎鍙ч梻顓濈瑹閸斅ゎ嚔娑?);
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
    @DisplayName("濞ｈ濮炵拠鍕啈閿涙艾褰茬憴浣镐紣閸楁洜娈戦悽銊﹀煕閸欘垯浜掔拠鍕啈閺堫亜鍙ч梻顓炰紣閸楁洩绱濋獮璺哄晸 COMMENT 閺冦儱绻?)
    void addCommentShouldWriteCommentAndLog() {
        Ticket current = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        printScenario("濞ｈ濮炵拠鍕啈", staff(), "瑜版挸澧犵拹鐔荤煑娴滃搫顕?PROCESSING 瀹搞儱宕熷ǎ璇插婢跺嫮鎮婄拠鍕啈");
        printTicket("瑜版挸澧犲銉ュ礋", current);
        when(ticketRepository.findVisibleById(TICKET_ID, STAFF_ID)).thenReturn(current);
        when(ticketCommentRepository.insert(any(TicketComment.class))).thenAnswer(invocation -> {
            TicketComment comment = invocation.getArgument(0);
            comment.setId(200L);
            System.out.printf("  鐠囧嫯顔戦崗銉ョ氨: id=%s, ticketId=%s, commenterId=%s, content=%s%n",
                    comment.getId(), comment.getTicketId(), comment.getCommenterId(), comment.getContent());
            return 1;
        });
        when(ticketCommentRepository.findById(200L)).thenReturn(TicketComment.builder()
                .id(200L)
                .ticketId(TICKET_ID)
                .commenterId(STAFF_ID)
                .commentType("USER_REPLY")
                .content("濮濓絽婀幒鎺撶叀閺冦儱绻?)
                .createdAt(LocalDateTime.of(2026, 4, 18, 19, 30, 0))
                .build());

        TicketComment result = ticketService.addComment(staff(), TICKET_ID, "濮濓絽婀幒鎺撶叀閺冦儱绻?);

        assertEquals(200L, result.getId());
        assertEquals("USER_REPLY", result.getCommentType());
        assertEquals("濮濓絽婀幒鎺撶叀閺冦儱绻?, result.getContent());
        assertEquals(LocalDateTime.of(2026, 4, 18, 19, 30, 0), result.getCreatedAt());
        verifyLog(OperationTypeEnum.COMMENT, STAFF_ID);
    }

    @Test
    @DisplayName("濞ｈ濮炵拠鍕啈閿涙艾鍑￠崗鎶芥４瀹搞儱宕熸稉宥堝厴缂佈呯敾鐠囧嫯顔?)
    void addCommentShouldRejectClosedTicket() {
        Ticket current = ticket(TicketStatusEnum.CLOSED, CREATOR_ID, STAFF_ID);
        printScenario("濞ｈ濮炵拠鍕啈婢惰精瑙?, staff(), "瀹告彃鍙ч梻顓炰紣閸楁洑绗夐懗鐣屾埛缂侇叀鎷烽崝鐘虹槑鐠?);
        printTicket("瑜版挸澧犲銉ュ礋", current);
        when(ticketRepository.findVisibleById(TICKET_ID, STAFF_ID)).thenReturn(current);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.addComment(staff(), TICKET_ID, "鏉╂鍏樻径鍕倞閸?)
        );

        assertEquals("TICKET_CLOSED", ex.getCode());
        printException(ex);
        verify(ticketCommentRepository, never()).insert(any());
        verify(operationLogRepository, never()).insert(any());
    }

    @Test
    @DisplayName("閸忔娊妫村銉ュ礋閿涙碍褰侀崡鏇氭眽閸欘垰鍙ч梻顓炲嚒鐟欙絽鍠呭銉ュ礋閿涘苯鑻熼崘?CLOSE 閺冦儱绻?)
    void closeTicketShouldAllowCreatorCloseResolvedTicket() {
        Ticket before = ticket(TicketStatusEnum.RESOLVED, CREATOR_ID, STAFF_ID);
        before.setSolutionSummary("瀹歌弓鎱ㄦ径?);
        Ticket after = ticket(TicketStatusEnum.CLOSED, CREATOR_ID, STAFF_ID);
        after.setSolutionSummary("瀹歌弓鎱ㄦ径?);
        printScenario("閸忔娊妫村銉ュ礋", user(), "閹绘劕宕熸禍鍝勫彠闂?RESOLVED 瀹搞儱宕熼敍宀€濮搁幀浣稿綁娑?CLOSED");
        printTicketFlow(before, after);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before, after);
        when(ticketRepository.updateStatus(
                TICKET_ID,
                TicketStatusEnum.RESOLVED,
                TicketStatusEnum.CLOSED,
                "瀹歌弓鎱ㄦ径?
        )).thenReturn(1);

        Ticket result = ticketService.closeTicket(user(), TICKET_ID);

        assertEquals(TicketStatusEnum.CLOSED, result.getStatus());
        verify(ticketRepository).updateStatus(
                TICKET_ID,
                TicketStatusEnum.RESOLVED,
                TicketStatusEnum.CLOSED,
                "瀹歌弓鎱ㄦ径?
        );
        verifyLog(OperationTypeEnum.CLOSE, CREATOR_ID);
    }

    @Test
    @DisplayName("閸掑棝銆夐弻銉嚄閿涙氨顓搁悶鍡楁喅鐠ф澘鍙忛柌蹇撳瀻妞ょ绱濋弲顕€鈧氨鏁ら幋鐤泲閸欘垵顫嗛懠鍐ㄦ纯閸掑棝銆?)
    void pageTicketsShouldUseDifferentScopeByRole() {
        TicketPageQueryDTO query = TicketPageQueryDTO.builder()
                .pageNo(1)
                .pageSize(10)
                .status(TicketStatusEnum.PROCESSING)
                .build();
        printScenario("閸掑棝銆夐弻銉嚄", admin(), "缁狅紕鎮婇崨妯荤叀閸忋劑鍣洪懠鍐ㄦ纯閿涘本娅橀柅姘辨暏閹撮攱鐓￠懛顏勭箒閸欘垵顫嗛懠鍐ㄦ纯");
        System.out.printf("  閺屻儴顕楅弶鈥叉: pageNo=%s, pageSize=%s, status=%s%n",
                query.getPageNo(), query.getPageSize(), query.getStatus());
        when(ticketRepository.pageAll("PROCESSING", null, null, 0, 10)).thenReturn(List.of(ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID)));
        when(ticketRepository.countAll("PROCESSING", null, null)).thenReturn(1L);
        when(ticketRepository.pageVisible(CREATOR_ID, "PROCESSING", null, null, 0, 10)).thenReturn(List.of());
        when(ticketRepository.countVisible(CREATOR_ID, "PROCESSING", null, null)).thenReturn(0L);

        PageResult<Ticket> adminPage = ticketService.pageTickets(admin(), query);
        PageResult<Ticket> userPage = ticketService.pageTickets(user(), query);

        assertEquals(1L, adminPage.getTotal());
        assertEquals(0L, userPage.getTotal());
        System.out.printf("  缁狅紕鎮婇崨妯肩波閺? total=%s, records=%s%n", adminPage.getTotal(), adminPage.getRecords().size());
        System.out.printf("  閺咁噣鈧氨鏁ら幋椋庣波閺? total=%s, records=%s%n", userPage.getTotal(), userPage.getRecords().size());
        verify(ticketRepository).pageAll("PROCESSING", null, null, 0, 10);
        verify(ticketRepository).pageVisible(CREATOR_ID, "PROCESSING", null, null, 0, 10);
    }

    @Test
    @DisplayName("璁ら宸ュ崟锛氶槦鍒楁垚鍛樺彲璁ら寰呭垎閰嶅伐鍗曞苟杩涘叆 PROCESSING")
    void claimTicketShouldAllowQueueMember() {
        Ticket before = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        before.setGroupId(30L);
        before.setQueueId(40L);
        Ticket after = ticket(TicketStatusEnum.PROCESSING, CREATOR_ID, STAFF_ID);
        after.setGroupId(30L);
        after.setQueueId(40L);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before, after);
        mockEnabledStaff(STAFF_ID);
        when(ticketQueueMemberService.isEnabledMember(40L, STAFF_ID)).thenReturn(true);
        when(ticketRepository.updateAssigneeAndStatus(
                TICKET_ID,
                STAFF_ID,
                TicketStatusEnum.PENDING_ASSIGN,
                TicketStatusEnum.PROCESSING
        )).thenReturn(1);

        Ticket result = ticketService.claimTicket(staff(), TICKET_ID);

        assertEquals(TicketStatusEnum.PROCESSING, result.getStatus());
        assertEquals(STAFF_ID, result.getAssigneeId());
        verifyLog(OperationTypeEnum.CLAIM, STAFF_ID);
    }

    @Test
    @DisplayName("璁ら宸ュ崟锛氶潪闃熷垪鎴愬憳涓旈潪缁勮礋璐ｄ汉涓嶈兘璁ら")
    void claimTicketShouldRejectUnrelatedStaff() {
        Ticket before = ticket(TicketStatusEnum.PENDING_ASSIGN, CREATOR_ID, null);
        before.setGroupId(30L);
        before.setQueueId(40L);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(before);
        mockEnabledStaff(OTHER_STAFF_ID);
        when(ticketQueueMemberService.isEnabledMember(40L, OTHER_STAFF_ID)).thenReturn(false);
        when(ticketGroupService.get(30L)).thenReturn(TicketGroup.builder()
                .id(30L)
                .ownerUserId(99L)
                .enabled(1)
                .build());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ticketService.claimTicket(otherStaff(), TICKET_ID)
        );

        assertEquals("TICKET_CLAIM_FORBIDDEN", ex.getCode());
        verify(ticketRepository, never()).updateAssigneeAndStatus(any(), any(), any(), any());
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
        printLog("閹垮秳缍旈弮銉ョ箶", log);
    }

    private void printScenario(String name, CurrentUser operator, String description) {
        System.out.printf("%n[濞村鐦崷鐑樻珯] %s%n", name);
        System.out.printf("  鐠囧瓨妲? %s%n", description);
        System.out.printf("  閹垮秳缍旀禍? userId=%s, username=%s, roles=%s%n",
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
        printTicket("濞翠浇娴嗛崜?, before);
        printTicket("濞翠浇娴嗛崥?, after);
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
        System.out.printf("  娑撴艾濮熷鍌氱埗: code=%s, message=%s%n", ex.getCode(), ex.getMessage());
    }

    private Ticket ticket(TicketStatusEnum status, Long creatorId, Long assigneeId) {
        return Ticket.builder()
                .id(TICKET_ID)
                .ticketNo("INC202604170001")
                .title("濞村鐦銉ュ礋")
                .description("濞村鐦幓蹇氬牚")
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

    private CurrentUser otherStaff() {
        return CurrentUser.builder()
                .userId(OTHER_STAFF_ID)
                .username("staff2")
                .roles(List.of("USER", "STAFF"))
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
