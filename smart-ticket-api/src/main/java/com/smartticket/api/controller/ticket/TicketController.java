package com.smartticket.api.controller.ticket;

import com.smartticket.api.assembler.ticket.TicketAssembler;
import com.smartticket.api.dto.ticket.AddTicketCommentRequestDTO;
import com.smartticket.api.dto.ticket.AssignTicketRequestDTO;
import com.smartticket.api.dto.ticket.BindTicketQueueRequestDTO;
import com.smartticket.api.dto.ticket.CreateTicketRequestDTO;
import com.smartticket.api.dto.ticket.DecideTicketApprovalRequestDTO;
import com.smartticket.api.dto.ticket.SubmitTicketApprovalRequestDTO;
import com.smartticket.api.dto.ticket.UpdateTicketStatusRequestDTO;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.support.TicketRequestParser;
import com.smartticket.api.vo.ticket.TicketApprovalVO;
import com.smartticket.api.vo.ticket.TicketCommentVO;
import com.smartticket.api.vo.ticket.TicketDetailVO;
import com.smartticket.api.vo.ticket.TicketSummaryVO;
import com.smartticket.api.vo.ticket.TicketVO;
import com.smartticket.biz.dto.ticket.TicketCreateCommandDTO;
import com.smartticket.biz.dto.ticket.TicketPageQueryDTO;
import com.smartticket.biz.dto.ticket.TicketSummaryDTO;
import com.smartticket.biz.dto.ticket.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.service.ticket.TicketService;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.entity.TicketApproval;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
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
 * 工单 HTTP 入口。
 *
 * <p>这里只做协议层工作：认证用户解析、请求体转命令、响应对象组装。
 * 具体业务流程统一下沉到 {@link TicketService}。</p>
 */
@Validated
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "工单", description = "工单接口")
public class TicketController {
    // 工单服务
    private final TicketService ticketService;
    // 当前用户解析器
    private final CurrentUserResolver currentUserResolver;
    // 工单请求解析器
    private final TicketRequestParser ticketRequestParser;
    // 工单装配器
    private final TicketAssembler ticketAssembler;

    /**
     * 构造工单控制器。
     */
    public TicketController(
            TicketService ticketService,
            CurrentUserResolver currentUserResolver,
            TicketRequestParser ticketRequestParser,
            TicketAssembler ticketAssembler
    ) {
        this.ticketService = ticketService;
        this.currentUserResolver = currentUserResolver;
        this.ticketRequestParser = ticketRequestParser;
        this.ticketAssembler = ticketAssembler;
    }

    /**
     * 创建工单。
     */
    @PostMapping
    @Operation(summary = "创建工单", description = "创建一张处于待分配状态的工单")
    public ApiResponse<TicketVO> createTicket(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTicketRequestDTO request
    ) {
        Ticket ticket = ticketService.createTicket(currentUserResolver.resolve(authentication), TicketCreateCommandDTO.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .type(ticketRequestParser.parseType(request.getType()))
                .typeProfile(request.getTypeProfile())
                .category(ticketRequestParser.parseCategory(request.getCategory()))
                .priority(ticketRequestParser.parsePriority(request.getPriority()))
                .idempotencyKey(resolveIdempotencyKey(idempotencyKey, request.getIdempotencyKey()))
                .build());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    /**
     * 获取工单详情。
     */
    @GetMapping("/{ticketId}")
    @Operation(summary = "获取工单详情")
    public ApiResponse<TicketDetailVO> getTicketDetail(
            Authentication authentication,
            @Parameter(description = "Ticket id") @PathVariable("ticketId") Long ticketId
    ) {
        return ApiResponse.success(ticketAssembler.toDetailVO(ticketService.getDetail(currentUserResolver.resolve(authentication), ticketId)));
    }

    /**
     * 获取工单摘要。
     */
    @GetMapping("/{ticketId}/summary")
    @Operation(summary = "获取工单摘要")
    public ApiResponse<TicketSummaryVO> getTicketSummary(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @RequestParam(value = "view", required = false) String view
    ) {
        TicketSummaryDTO summary = ticketService.getSummary(
                currentUserResolver.resolve(authentication),
                ticketId,
                ticketRequestParser.parseSummaryView(view)
        );
        return ApiResponse.success(ticketAssembler.toSummaryVO(summary));
    }

    /**
     * 获取审批。
     */
    @GetMapping("/{ticketId}/approval")
    @Operation(summary = "获取工单审批信息")
    public ApiResponse<TicketApprovalVO> getApproval(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        TicketApproval approval = ticketService.getApproval(currentUserResolver.resolve(authentication), ticketId);
        return ApiResponse.success(ticketAssembler.toApprovalVO(approval));
    }

    /**
     * 提交审批。
     */
    @PostMapping("/{ticketId}/approval/submit")
    @Operation(summary = "提交审批")
    public ApiResponse<TicketApprovalVO> submitApproval(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody SubmitTicketApprovalRequestDTO request
    ) {
        TicketApproval approval = ticketService.submitApproval(
                currentUserResolver.resolve(authentication),
                ticketId,
                request.getTemplateId(),
                request.getApproverId(),
                request.getSubmitComment()
        );
        return ApiResponse.success(ticketAssembler.toApprovalVO(approval));
    }

    /**
     * 审批通过。
     */
    @PostMapping("/{ticketId}/approval/approve")
    @Operation(summary = "审批通过")
    public ApiResponse<TicketApprovalVO> approve(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody DecideTicketApprovalRequestDTO request
    ) {
        TicketApproval approval = ticketService.approve(currentUserResolver.resolve(authentication), ticketId, request.getDecisionComment());
        return ApiResponse.success(ticketAssembler.toApprovalVO(approval));
    }

    /**
     * 审批拒绝。
     */
    @PostMapping("/{ticketId}/approval/reject")
    @Operation(summary = "审批拒绝")
    public ApiResponse<TicketApprovalVO> reject(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody DecideTicketApprovalRequestDTO request
    ) {
        TicketApproval approval = ticketService.reject(currentUserResolver.resolve(authentication), ticketId, request.getDecisionComment());
        return ApiResponse.success(ticketAssembler.toApprovalVO(approval));
    }

    /**
     * 分页查询工单。
     */
    @GetMapping
    @Operation(summary = "分页查询工单")
    public ApiResponse<PageResult<TicketVO>> page工单(
            Authentication authentication,
            @Min(value = 1, message = "pageNo must be >= 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority
    ) {
        PageResult<Ticket> page = ticketService.page工单(currentUserResolver.resolve(authentication), TicketPageQueryDTO.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .status(ticketRequestParser.parseStatus(status))
                .type(ticketRequestParser.parseType(type))
                .category(ticketRequestParser.parseCategory(category))
                .priority(ticketRequestParser.parsePriority(priority))
                .build());
        return ApiResponse.success(PageResult.<TicketVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(ticketAssembler::toVO).toList())
                .build());
    }

    /**
     * 分派工单。
     */
    @PutMapping("/{ticketId}/assign")
    @Operation(summary = "分派工单")
    public ApiResponse<TicketVO> assignTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AssignTicketRequestDTO request
    ) {
        Ticket ticket = ticketService.assignTicket(currentUserResolver.resolve(authentication), ticketId, request.getAssigneeId());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    /**
     * 认领工单。
     */
    @PutMapping("/{ticketId}/claim")
    @Operation(summary = "认领工单")
    public ApiResponse<TicketVO> claimTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        Ticket ticket = ticketService.claimTicket(currentUserResolver.resolve(authentication), ticketId);
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    /**
     * 绑定工单队列。
     */
    @PutMapping("/{ticketId}/queue")
    @Operation(summary = "绑定工单队列")
    public ApiResponse<TicketVO> bindTicketQueue(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody BindTicketQueueRequestDTO request
    ) {
        Ticket ticket = ticketService.bindTicketQueue(
                currentUserResolver.resolve(authentication),
                ticketId,
                request.getGroupId(),
                request.getQueueId()
        );
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    /**
     * 转派工单。
     */
    @PutMapping("/{ticketId}/transfer")
    @Operation(summary = "转派工单")
    public ApiResponse<TicketVO> transferTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AssignTicketRequestDTO request
    ) {
        Ticket ticket = ticketService.transferTicket(currentUserResolver.resolve(authentication), ticketId, request.getAssigneeId());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    /**
     * 更新状态。
     */
    @PutMapping("/{ticketId}/status")
    @Operation(summary = "更新工单状态")
    public ApiResponse<TicketVO> updateStatus(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequestDTO request
    ) {
        Ticket ticket = ticketService.updateStatus(currentUserResolver.resolve(authentication), ticketId, TicketUpdateStatusCommandDTO.builder()
                .targetStatus(ticketRequestParser.parseStatus(request.getTargetStatus()))
                .solutionSummary(request.getSolutionSummary())
                .build());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    /**
     * 新增评论。
     */
    @PostMapping("/{ticketId}/comments")
    @Operation(summary = "新增工单评论")
    public ApiResponse<TicketCommentVO> addComment(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AddTicketCommentRequestDTO request
    ) {
        return ApiResponse.success(ticketAssembler.toCommentVO(
                ticketService.addComment(currentUserResolver.resolve(authentication), ticketId, request.getContent())
        ));
    }

    /**
     * 关闭工单。
     */
    @PutMapping("/{ticketId}/close")
    @Operation(summary = "关闭工单")
    public ApiResponse<TicketVO> closeTicket(Authentication authentication, @PathVariable("ticketId") Long ticketId) {
        Ticket ticket = ticketService.closeTicket(currentUserResolver.resolve(authentication), ticketId);
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    /**
     * 解析幂等键。
     */
    private String resolveIdempotencyKey(String headerValue, String bodyValue) {
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        return bodyValue;
    }
}
