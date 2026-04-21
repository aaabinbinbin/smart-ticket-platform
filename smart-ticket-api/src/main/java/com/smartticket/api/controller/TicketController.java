package com.smartticket.api.controller;

import com.smartticket.api.assembler.TicketAssembler;
import com.smartticket.api.dto.ticket.AddTicketCommentRequestDTO;
import com.smartticket.api.dto.ticket.AssignTicketRequestDTO;
import com.smartticket.api.dto.ticket.BindTicketQueueRequestDTO;
import com.smartticket.api.dto.ticket.CreateTicketRequestDTO;
import com.smartticket.api.dto.ticket.UpdateTicketStatusRequestDTO;
import com.smartticket.api.vo.ticket.TicketCommentVO;
import com.smartticket.api.vo.ticket.TicketDetailVO;
import com.smartticket.api.vo.ticket.TicketVO;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.dto.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.TicketCommandService;
import com.smartticket.biz.service.TicketCommentService;
import com.smartticket.biz.service.TicketQueryService;
import com.smartticket.biz.service.TicketQueueBindingService;
import com.smartticket.biz.service.TicketWorkflowService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 宸ュ崟 HTTP 鎺ュ彛鎺у埗鍣ㄣ€? *
 * <p>璐熻矗鎺ユ敹宸ュ崟鐩稿叧 HTTP 璇锋眰锛屽畬鎴愬弬鏁拌浆鎹㈠拰鍝嶅簲缁勮锛涘叿浣撲笟鍔¤鍒欑敱 {@link TicketService} 澶勭悊銆?/p>
 */
@Validated
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "宸ュ崟鏍稿績鎺ュ彛", description = "鍒涘缓銆佹煡璇€佸垎閰嶃€佽浆娲俱€佺姸鎬佹洿鏂般€佽瘎璁哄拰鍏抽棴宸ュ崟")
public class TicketController {
    private final TicketCommandService ticketCommandService;
    private final TicketQueryService ticketQueryService;
    private final TicketWorkflowService ticketWorkflowService;
    private final TicketCommentService ticketCommentService;
    private final TicketQueueBindingService ticketQueueBindingService;
    private final TicketAssembler ticketAssembler;

    public TicketController(
            TicketCommandService ticketCommandService,
            TicketQueryService ticketQueryService,
            TicketWorkflowService ticketWorkflowService,
            TicketCommentService ticketCommentService,
            TicketQueueBindingService ticketQueueBindingService,
            TicketAssembler ticketAssembler
    ) {
        this.ticketCommandService = ticketCommandService;
        this.ticketQueryService = ticketQueryService;
        this.ticketWorkflowService = ticketWorkflowService;
        this.ticketCommentService = ticketCommentService;
        this.ticketQueueBindingService = ticketQueueBindingService;
        this.ticketAssembler = ticketAssembler;
    }

    @PostMapping
    @Operation(summary = "鍒涘缓宸ュ崟", description = "鍒涘缓鍚庣殑宸ュ崟鍒濆鐘舵€佷负 PENDING_ASSIGN")
    public ApiResponse<TicketVO> createTicket(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTicketRequestDTO request
    ) {
        Ticket ticket = ticketCommandService.createTicket(currentUser(authentication), TicketCreateCommandDTO.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(parseCategory(request.getCategory()))
                .priority(parsePriority(request.getPriority()))
                .idempotencyKey(resolveIdempotencyKey(idempotencyKey, request.getIdempotencyKey()))
                .build());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "鏌ヨ宸ュ崟璇︽儏", description = "杩斿洖宸ュ崟涓讳俊鎭€佽瘎璁哄垪琛ㄥ拰鎿嶄綔鏃ュ織")
    public ApiResponse<TicketDetailVO> getTicketDetail(
            Authentication authentication,
            @Parameter(description = "宸ュ崟 ID") @PathVariable("ticketId") Long ticketId
    ) {
        return ApiResponse.success(ticketAssembler.toDetailVO(ticketQueryService.getDetail(currentUser(authentication), ticketId)));
    }

    @GetMapping
    @Operation(summary = "鍒嗛〉鏌ヨ宸ュ崟鍒楄〃", description = "绠＄悊鍛樺彲鐪嬪叏閮紝鏅€氱敤鎴峰彧鐪嬭嚜宸卞垱寤烘垨褰撳墠璐熻矗鐨勫伐鍗?)
    public ApiResponse<PageResult<TicketVO>> pageTickets(
            Authentication authentication,
            @Min(value = 1, message = "椤电爜涓嶈兘灏忎簬 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "姣忛〉澶у皬涓嶈兘灏忎簬 1")
            @Max(value = 100, message = "姣忛〉澶у皬涓嶈兘瓒呰繃 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority
    ) {
        PageResult<Ticket> page = ticketQueryService.pageTickets(currentUser(authentication), TicketPageQueryDTO.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .status(parseStatus(status))
                .category(parseCategory(category))
                .priority(parsePriority(priority))
                .build());
        PageResult<TicketVO> result = PageResult.<TicketVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(ticketAssembler::toVO).toList())
                .build();
        return ApiResponse.success(result);
    }

    @PutMapping("/{ticketId}/assign")
    @Operation(summary = "鍒嗛厤宸ュ崟", description = "绠＄悊鍛樺垎閰嶅緟鍒嗛厤宸ュ崟锛岀姸鎬佷粠 PENDING_ASSIGN 娴佽浆涓?PROCESSING")
    public ApiResponse<TicketVO> assignTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AssignTicketRequestDTO request
    ) {
        Ticket ticket = ticketWorkflowService.assignTicket(currentUser(authentication), ticketId, request.getAssigneeId());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/claim")
    @Operation(summary = "认领工单", description = "队列成员、组负责人或管理员可认领待分配工单")
    public ApiResponse<TicketVO> claimTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        Ticket ticket = ticketWorkflowService.claimTicket(currentUser(authentication), ticketId);
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/queue")
    @Operation(summary = "缁戝畾宸ュ崟闃熷垪", description = "绠＄悊鍛樺皢宸ュ崟缁戝畾鍒版寚瀹氬伐鍗曠粍鍜岄槦鍒楋紝涓嶄慨鏀瑰鐞嗕汉鍜岀姸鎬?)
    public ApiResponse<TicketVO> bindTicketQueue(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody BindTicketQueueRequestDTO request
    ) {
        Ticket ticket = ticketQueueBindingService.bindTicketQueue(
                currentUser(authentication),
                ticketId,
                request.getGroupId(),
                request.getQueueId()
        );
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/transfer")
    @Operation(summary = "杞淳宸ュ崟", description = "褰撳墠璐熻矗浜烘垨绠＄悊鍛樺彲杞淳澶勭悊涓殑宸ュ崟")
    public ApiResponse<TicketVO> transferTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AssignTicketRequestDTO request
    ) {
        Ticket ticket = ticketWorkflowService.transferTicket(currentUser(authentication), ticketId, request.getAssigneeId());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/status")
    @Operation(summary = "鏇存柊宸ュ崟鐘舵€?, description = "鍙厑璁?PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED")
    public ApiResponse<TicketVO> updateStatus(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequestDTO request
    ) {
        Ticket ticket = ticketWorkflowService.updateStatus(currentUser(authentication), ticketId, TicketUpdateStatusCommandDTO.builder()
                .targetStatus(parseStatus(request.getTargetStatus()))
                .solutionSummary(request.getSolutionSummary())
                .build());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PostMapping("/{ticketId}/comments")
    @Operation(summary = "娣诲姞宸ュ崟璇勮", description = "鎻愬崟浜恒€佸綋鍓嶈礋璐ｄ汉鎴栫鐞嗗憳鍙互瀵规湭鍏抽棴宸ュ崟娣诲姞璇勮")
    public ApiResponse<TicketCommentVO> addComment(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AddTicketCommentRequestDTO request
    ) {
        return ApiResponse.success(ticketAssembler.toCommentVO(
                ticketCommentService.addComment(currentUser(authentication), ticketId, request.getContent())
        ));
    }

    @PutMapping("/{ticketId}/close")
    @Operation(summary = "鍏抽棴宸ュ崟", description = "鎻愬崟浜烘垨绠＄悊鍛樺叧闂凡瑙ｅ喅宸ュ崟锛岀姸鎬佷粠 RESOLVED 娴佽浆涓?CLOSED")
    public ApiResponse<TicketVO> closeTicket(Authentication authentication, @PathVariable("ticketId") Long ticketId) {
        Ticket ticket = ticketWorkflowService.closeTicket(currentUser(authentication), ticketId);
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    private CurrentUser currentUser(Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        return CurrentUser.builder()
                .userId(authUser.getUserId())
                .username(authUser.getUsername())
                .roles(authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(authority -> authority.replace("ROLE_", ""))
                        .toList())
                .build();
    }

    private TicketStatusEnum parseStatus(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketStatusEnum.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, code);
        }
    }

    private TicketCategoryEnum parseCategory(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketCategoryEnum.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_CATEGORY, code);
        }
    }

    private TicketPriorityEnum parsePriority(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketPriorityEnum.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_PRIORITY, code);
        }
    }

    private String resolveIdempotencyKey(String headerValue, String bodyValue) {
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        return bodyValue;
    }
}
