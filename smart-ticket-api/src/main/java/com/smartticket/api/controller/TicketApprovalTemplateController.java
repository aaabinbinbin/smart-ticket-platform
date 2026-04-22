package com.smartticket.api.controller;

import com.smartticket.api.assembler.P1ConfigAssembler;
import com.smartticket.api.dto.p1.TicketApprovalTemplateRequestDTO;
import com.smartticket.api.dto.p1.UpdateEnabledRequest;
import com.smartticket.api.vo.p1.TicketApprovalTemplateVO;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.dto.TicketApprovalTemplateCommandDTO;
import com.smartticket.biz.dto.TicketApprovalTemplatePageQueryDTO;
import com.smartticket.biz.dto.TicketApprovalTemplateStepCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.TicketApprovalTemplateService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketApprovalTemplate;
import com.smartticket.domain.enums.TicketTypeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/ticket-approval-templates")
@Tag(name = "P1 ����ģ��", description = "����ģ�����á���ͣ�Ͳ�ѯ")
public class TicketApprovalTemplateController {
    private final TicketApprovalTemplateService ticketApprovalTemplateService;
    private final P1ConfigAssembler assembler;

    public TicketApprovalTemplateController(TicketApprovalTemplateService ticketApprovalTemplateService, P1ConfigAssembler assembler) {
        this.ticketApprovalTemplateService = ticketApprovalTemplateService;
        this.assembler = assembler;
    }

    @PostMapping
    @Operation(summary = "��������ģ��")
    public ApiResponse<TicketApprovalTemplateVO> create(
            Authentication authentication,
            @Valid @RequestBody TicketApprovalTemplateRequestDTO request
    ) {
        TicketApprovalTemplate template = ticketApprovalTemplateService.create(currentUser(authentication), toCommand(request));
        return ApiResponse.success(assembler.toApprovalTemplateVO(template));
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "��������ģ��")
    public ApiResponse<TicketApprovalTemplateVO> update(
            Authentication authentication,
            @PathVariable("templateId") Long templateId,
            @Valid @RequestBody TicketApprovalTemplateRequestDTO request
    ) {
        TicketApprovalTemplate template = ticketApprovalTemplateService.update(currentUser(authentication), templateId, toCommand(request));
        return ApiResponse.success(assembler.toApprovalTemplateVO(template));
    }

    @PatchMapping("/{templateId}/enabled")
    @Operation(summary = "���û�ͣ������ģ��")
    public ApiResponse<TicketApprovalTemplateVO> updateEnabled(
            Authentication authentication,
            @PathVariable("templateId") Long templateId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketApprovalTemplate template = ticketApprovalTemplateService.updateEnabled(currentUser(authentication), templateId, request.getEnabled());
        return ApiResponse.success(assembler.toApprovalTemplateVO(template));
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "��ѯ����ģ������")
    public ApiResponse<TicketApprovalTemplateVO> get(@PathVariable("templateId") Long templateId) {
        return ApiResponse.success(assembler.toApprovalTemplateVO(ticketApprovalTemplateService.get(templateId)));
    }

    @GetMapping
    @Operation(summary = "��ҳ��ѯ����ģ��")
    public ApiResponse<PageResult<TicketApprovalTemplateVO>> page(
            @Min(value = 1, message = "ҳ�벻��С�� 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "ÿҳ��С����С�� 1")
            @Max(value = 100, message = "ÿҳ��С���ܳ��� 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "ticketType", required = false) String ticketType,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        PageResult<TicketApprovalTemplate> page = ticketApprovalTemplateService.page(TicketApprovalTemplatePageQueryDTO.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .ticketType(parseType(ticketType))
                .enabled(enabled)
                .build());
        return ApiResponse.success(PageResult.<TicketApprovalTemplateVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(assembler::toApprovalTemplateVO).toList())
                .build());
    }

    private TicketApprovalTemplateCommandDTO toCommand(TicketApprovalTemplateRequestDTO request) {
        List<TicketApprovalTemplateStepCommandDTO> steps = request.getSteps().stream()
                .map(step -> TicketApprovalTemplateStepCommandDTO.builder()
                        .stepOrder(step.getStepOrder())
                        .stepName(step.getStepName())
                        .approverId(step.getApproverId())
                        .build())
                .toList();
        return TicketApprovalTemplateCommandDTO.builder()
                .templateName(request.getTemplateName())
                .ticketType(parseType(request.getTicketType()))
                .description(request.getDescription())
                .enabled(request.getEnabled())
                .steps(steps)
                .build();
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
}
