package com.smartticket.api.controller;

import com.smartticket.api.assembler.TicketAssembler;
import com.smartticket.api.dto.ticket.AddTicketCommentRequestDTO;
import com.smartticket.api.dto.ticket.AssignTicketRequestDTO;
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
import com.smartticket.biz.service.TicketService;
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
 * 工单 HTTP 接口控制器。
 *
 * <p>负责接收工单相关 HTTP 请求，完成参数转换和响应组装；具体业务规则由 {@link TicketService} 处理。</p>
 */
@Validated
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "工单核心接口", description = "创建、查询、分配、转派、状态更新、评论和关闭工单")
public class TicketController {
    private final TicketService ticketService;
    private final TicketAssembler ticketAssembler;

    public TicketController(TicketService ticketService, TicketAssembler ticketAssembler) {
        this.ticketService = ticketService;
        this.ticketAssembler = ticketAssembler;
    }

    @PostMapping
    @Operation(summary = "创建工单", description = "创建后的工单初始状态为 PENDING_ASSIGN")
    public ApiResponse<TicketVO> createTicket(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTicketRequestDTO request
    ) {
        Ticket ticket = ticketService.createTicket(currentUser(authentication), TicketCreateCommandDTO.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(parseCategory(request.getCategory()))
                .priority(parsePriority(request.getPriority()))
                .idempotencyKey(resolveIdempotencyKey(idempotencyKey, request.getIdempotencyKey()))
                .build());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "查询工单详情", description = "返回工单主信息、评论列表和操作日志")
    public ApiResponse<TicketDetailVO> getTicketDetail(
            Authentication authentication,
            @Parameter(description = "工单 ID") @PathVariable("ticketId") Long ticketId
    ) {
        return ApiResponse.success(ticketAssembler.toDetailVO(ticketService.getDetail(currentUser(authentication), ticketId)));
    }

    @GetMapping
    @Operation(summary = "分页查询工单列表", description = "管理员可看全部，普通用户只看自己创建或当前负责的工单")
    public ApiResponse<PageResult<TicketVO>> pageTickets(
            Authentication authentication,
            @Min(value = 1, message = "页码不能小于 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "每页大小不能小于 1")
            @Max(value = 100, message = "每页大小不能超过 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority
    ) {
        PageResult<Ticket> page = ticketService.pageTickets(currentUser(authentication), TicketPageQueryDTO.builder()
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
    @Operation(summary = "分配工单", description = "管理员分配待分配工单，状态从 PENDING_ASSIGN 流转为 PROCESSING")
    public ApiResponse<TicketVO> assignTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AssignTicketRequestDTO request
    ) {
        Ticket ticket = ticketService.assignTicket(currentUser(authentication), ticketId, request.getAssigneeId());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/transfer")
    @Operation(summary = "转派工单", description = "当前负责人或管理员可转派处理中的工单")
    public ApiResponse<TicketVO> transferTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AssignTicketRequestDTO request
    ) {
        Ticket ticket = ticketService.transferTicket(currentUser(authentication), ticketId, request.getAssigneeId());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/status")
    @Operation(summary = "更新工单状态", description = "只允许 PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED")
    public ApiResponse<TicketVO> updateStatus(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequestDTO request
    ) {
        Ticket ticket = ticketService.updateStatus(currentUser(authentication), ticketId, TicketUpdateStatusCommandDTO.builder()
                .targetStatus(parseStatus(request.getTargetStatus()))
                .solutionSummary(request.getSolutionSummary())
                .build());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PostMapping("/{ticketId}/comments")
    @Operation(summary = "添加工单评论", description = "提单人、当前负责人或管理员可以对未关闭工单添加评论")
    public ApiResponse<TicketCommentVO> addComment(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AddTicketCommentRequestDTO request
    ) {
        return ApiResponse.success(ticketAssembler.toCommentVO(
                ticketService.addComment(currentUser(authentication), ticketId, request.getContent())
        ));
    }

    @PutMapping("/{ticketId}/close")
    @Operation(summary = "关闭工单", description = "提单人或管理员关闭已解决工单，状态从 RESOLVED 流转为 CLOSED")
    public ApiResponse<TicketVO> closeTicket(Authentication authentication, @PathVariable("ticketId") Long ticketId) {
        Ticket ticket = ticketService.closeTicket(currentUser(authentication), ticketId);
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
