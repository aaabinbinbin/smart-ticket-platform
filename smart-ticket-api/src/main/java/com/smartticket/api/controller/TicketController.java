package com.smartticket.api.controller;

import com.smartticket.api.assembler.TicketAssembler;
import com.smartticket.api.dto.ticket.AddTicketCommentRequestDTO;
import com.smartticket.api.dto.ticket.AssignTicketRequestDTO;
import com.smartticket.api.dto.ticket.BindTicketQueueRequestDTO;
import com.smartticket.api.dto.ticket.CreateTicketRequestDTO;
import com.smartticket.api.dto.ticket.DecideTicketApprovalRequestDTO;
import com.smartticket.api.dto.ticket.SubmitTicketApprovalRequestDTO;
import com.smartticket.api.dto.ticket.UpdateTicketStatusRequestDTO;
import com.smartticket.api.vo.ticket.TicketApprovalVO;
import com.smartticket.api.vo.ticket.TicketCommentVO;
import com.smartticket.api.vo.ticket.TicketDetailVO;
import com.smartticket.api.vo.ticket.TicketSummaryVO;
import com.smartticket.api.vo.ticket.TicketVO;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.dto.TicketCreateCommandDTO;
import com.smartticket.biz.dto.TicketPageQueryDTO;
import com.smartticket.biz.dto.TicketSummaryDTO;
import com.smartticket.biz.dto.TicketUpdateStatusCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.TicketApprovalService;
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
import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketSummaryViewEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
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

@Validated
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "�������Ľӿ�", description = "��������ѯ�����䡢���졢ת�ɡ�������״̬���¡����ۺ͹رչ���")
public class TicketController {
    private final TicketCommandService ticketCommandService;
    private final TicketQueryService ticketQueryService;
    private final TicketWorkflowService ticketWorkflowService;
    private final TicketCommentService ticketCommentService;
    private final TicketQueueBindingService ticketQueueBindingService;
    private final TicketApprovalService ticketApprovalService;
    private final TicketAssembler ticketAssembler;

    public TicketController(
            TicketCommandService ticketCommandService,
            TicketQueryService ticketQueryService,
            TicketWorkflowService ticketWorkflowService,
            TicketCommentService ticketCommentService,
            TicketQueueBindingService ticketQueueBindingService,
            TicketApprovalService ticketApprovalService,
            TicketAssembler ticketAssembler
    ) {
        this.ticketCommandService = ticketCommandService;
        this.ticketQueryService = ticketQueryService;
        this.ticketWorkflowService = ticketWorkflowService;
        this.ticketCommentService = ticketCommentService;
        this.ticketQueueBindingService = ticketQueueBindingService;
        this.ticketApprovalService = ticketApprovalService;
        this.ticketAssembler = ticketAssembler;
    }

    @PostMapping
    @Operation(summary = "��������", description = "������Ĺ�����ʼ״̬Ϊ PENDING_ASSIGN")
    public ApiResponse<TicketVO> createTicket(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTicketRequestDTO request
    ) {
        Ticket ticket = ticketCommandService.createTicket(currentUser(authentication), TicketCreateCommandDTO.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .type(parseType(request.getType()))
                .typeProfile(request.getTypeProfile())
                .category(parseCategory(request.getCategory()))
                .priority(parsePriority(request.getPriority()))
                .idempotencyKey(resolveIdempotencyKey(idempotencyKey, request.getIdempotencyKey()))
                .build());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "��ѯ��������", description = "���ع�������Ϣ��������Ϣ�������б�Ͳ�����־")
    public ApiResponse<TicketDetailVO> getTicketDetail(
            Authentication authentication,
            @Parameter(description = "���� ID") @PathVariable("ticketId") Long ticketId
    ) {
        return ApiResponse.success(ticketAssembler.toDetailVO(ticketQueryService.getDetail(currentUser(authentication), ticketId)));
    }

    @GetMapping("/{ticketId}/summary")
    @Operation(summary = "查询工单摘要", description = "支持提单人、处理人和管理员三种摘要视角")
    public ApiResponse<TicketSummaryVO> getTicketSummary(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @RequestParam(value = "view", required = false) String view
    ) {
        TicketSummaryDTO summary = ticketQueryService.getSummary(
                currentUser(authentication),
                ticketId,
                parseSummaryView(view)
        );
        return ApiResponse.success(ticketAssembler.toSummaryVO(summary));
    }

    @GetMapping("/{ticketId}/approval")
    @Operation(summary = "��ѯ������������", description = "������Ҫ�����Ĺ�������������¼")
    public ApiResponse<TicketApprovalVO> getApproval(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        TicketApproval approval = ticketApprovalService.getApproval(currentUser(authentication), ticketId);
        return ApiResponse.success(ticketAssembler.toApprovalVO(approval));
    }

    @PostMapping("/{ticketId}/approval/submit")
    @Operation(summary = "�ύ��������", description = "֧�ְ�ģ�����ɶ༶������Ҳ֧�ֶ��׵�������")
    public ApiResponse<TicketApprovalVO> submitApproval(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody SubmitTicketApprovalRequestDTO request
    ) {
        TicketApproval approval = ticketApprovalService.submitApproval(
                currentUser(authentication),
                ticketId,
                request.getTemplateId(),
                request.getApproverId(),
                request.getSubmitComment()
        );
        return ApiResponse.success(ticketAssembler.toApprovalVO(approval));
    }

    @PostMapping("/{ticketId}/approval/approve")
    @Operation(summary = "����ͨ��", description = "��ǰ���������˻����Ա��ִ������ͨ��")
    public ApiResponse<TicketApprovalVO> approve(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody DecideTicketApprovalRequestDTO request
    ) {
        TicketApproval approval = ticketApprovalService.approve(currentUser(authentication), ticketId, request.getDecisionComment());
        return ApiResponse.success(ticketAssembler.toApprovalVO(approval));
    }

    @PostMapping("/{ticketId}/approval/reject")
    @Operation(summary = "��������", description = "��ǰ���������˻����Ա��ִ����������")
    public ApiResponse<TicketApprovalVO> reject(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody DecideTicketApprovalRequestDTO request
    ) {
        TicketApproval approval = ticketApprovalService.reject(currentUser(authentication), ticketId, request.getDecisionComment());
        return ApiResponse.success(ticketAssembler.toApprovalVO(approval));
    }

    @GetMapping
    @Operation(summary = "��ҳ��ѯ�����б�", description = "����Ա�ɲ鿴ȫ������ͨ�û������Լ�������ǰ����Ĺ���")
    public ApiResponse<PageResult<TicketVO>> pageTickets(
            Authentication authentication,
            @Min(value = 1, message = "ҳ�벻��С�� 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "ÿҳ��С����С�� 1")
            @Max(value = 100, message = "ÿҳ��С���ܳ��� 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority
    ) {
        PageResult<Ticket> page = ticketQueryService.pageTickets(currentUser(authentication), TicketPageQueryDTO.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .status(parseStatus(status))
                .type(parseType(type))
                .category(parseCategory(category))
                .priority(parsePriority(priority))
                .build());
        return ApiResponse.success(PageResult.<TicketVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(ticketAssembler::toVO).toList())
                .build());
    }

    @PutMapping("/{ticketId}/assign")
    @Operation(summary = "���乤��", description = "����Ա�������乤������������ˣ����ƽ��� PROCESSING")
    public ApiResponse<TicketVO> assignTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AssignTicketRequestDTO request
    ) {
        Ticket ticket = ticketWorkflowService.assignTicket(currentUser(authentication), ticketId, request.getAssigneeId());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/claim")
    @Operation(summary = "���칤��", description = "���г�Ա���鸺���˻����Ա����������乤��")
    public ApiResponse<TicketVO> claimTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        Ticket ticket = ticketWorkflowService.claimTicket(currentUser(authentication), ticketId);
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/queue")
    @Operation(summary = "�󶨹�������", description = "����Ա�������󶨵�ָ��������Ͷ��У����޸Ĵ����˺�״̬")
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
    @Operation(summary = "ת�ɹ���", description = "��ǰ�����˻����Ա��ת�ɴ����й���")
    public ApiResponse<TicketVO> transferTicket(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId,
            @Valid @RequestBody AssignTicketRequestDTO request
    ) {
        Ticket ticket = ticketWorkflowService.transferTicket(currentUser(authentication), ticketId, request.getAssigneeId());
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    @PutMapping("/{ticketId}/status")
    @Operation(summary = "���¹���״̬", description = "ֻ���� PENDING_ASSIGN -> PROCESSING -> RESOLVED -> CLOSED")
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
    @Operation(summary = "��ӹ�������", description = "�ᵥ�ˡ���ǰ�����˻����Ա���Զ�δ�رչ����������")
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
    @Operation(summary = "�رչ���", description = "�ᵥ�˻����Ա�ر��ѽ������")
    public ApiResponse<TicketVO> closeTicket(Authentication authentication, @PathVariable("ticketId") Long ticketId) {
        Ticket ticket = ticketWorkflowService.closeTicket(currentUser(authentication), ticketId);
        return ApiResponse.success(ticketAssembler.toVO(ticket));
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BusinessException(BusinessErrorCode.UNAUTHORIZED);
        }
        return CurrentUser.builder()
                .userId(authUser.getUserId())
                .username(authUser.getUsername())
                .roles(authentication.getAuthorities().stream()
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
            return TicketStatusEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_STATUS, code);
        }
    }

    private TicketTypeEnum parseType(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketTypeEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_TYPE, code);
        }
    }

    private TicketCategoryEnum parseCategory(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketCategoryEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_CATEGORY, code);
        }
    }

    private TicketPriorityEnum parsePriority(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketPriorityEnum.fromCode(code.trim().toUpperCase());
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

    private TicketSummaryViewEnum parseSummaryView(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return TicketSummaryViewEnum.fromCode(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(BusinessErrorCode.INVALID_TICKET_SUMMARY_VIEW, code);
        }
    }
}
