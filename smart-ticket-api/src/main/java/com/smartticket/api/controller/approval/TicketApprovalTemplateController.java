package com.smartticket.api.controller.approval;

import com.smartticket.api.assembler.config.P1ConfigAssembler;
import com.smartticket.api.dto.approval.TicketApprovalTemplateRequestDTO;
import com.smartticket.api.dto.common.UpdateEnabledRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.vo.approval.TicketApprovalTemplateVO;
import com.smartticket.biz.dto.approval.TicketApprovalTemplateCommandDTO;
import com.smartticket.biz.dto.approval.TicketApprovalTemplatePageQueryDTO;
import com.smartticket.biz.dto.approval.TicketApprovalTemplateStepCommandDTO;
import com.smartticket.biz.service.approval.TicketApprovalAdminService;
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
@Tag(name = "Approval Templates", description = "Approval template management")
public class TicketApprovalTemplateController {
    private final TicketApprovalAdminService ticketApprovalAdminService;
    private final CurrentUserResolver currentUserResolver;
    private final P1ConfigAssembler assembler;

    public TicketApprovalTemplateController(
            TicketApprovalAdminService ticketApprovalAdminService,
            CurrentUserResolver currentUserResolver,
            P1ConfigAssembler assembler
    ) {
        this.ticketApprovalAdminService = ticketApprovalAdminService;
        this.currentUserResolver = currentUserResolver;
        this.assembler = assembler;
    }

    @PostMapping
    @Operation(summary = "Create approval template")
    public ApiResponse<TicketApprovalTemplateVO> create(
            Authentication authentication,
            @Valid @RequestBody TicketApprovalTemplateRequestDTO request
    ) {
        TicketApprovalTemplate template = ticketApprovalAdminService.createTemplate(currentUserResolver.resolve(authentication), toCommand(request));
        return ApiResponse.success(assembler.toApprovalTemplateVO(template));
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "Update approval template")
    public ApiResponse<TicketApprovalTemplateVO> update(
            Authentication authentication,
            @PathVariable("templateId") Long templateId,
            @Valid @RequestBody TicketApprovalTemplateRequestDTO request
    ) {
        TicketApprovalTemplate template = ticketApprovalAdminService.updateTemplate(currentUserResolver.resolve(authentication), templateId, toCommand(request));
        return ApiResponse.success(assembler.toApprovalTemplateVO(template));
    }

    @PatchMapping("/{templateId}/enabled")
    @Operation(summary = "Enable or disable approval template")
    public ApiResponse<TicketApprovalTemplateVO> updateEnabled(
            Authentication authentication,
            @PathVariable("templateId") Long templateId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketApprovalTemplate template = ticketApprovalAdminService.updateTemplateEnabled(currentUserResolver.resolve(authentication), templateId, request.getEnabled());
        return ApiResponse.success(assembler.toApprovalTemplateVO(template));
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "Get approval template")
    public ApiResponse<TicketApprovalTemplateVO> get(@PathVariable("templateId") Long templateId) {
        return ApiResponse.success(assembler.toApprovalTemplateVO(ticketApprovalAdminService.getTemplate(templateId)));
    }

    @GetMapping
    @Operation(summary = "Page approval templates")
    public ApiResponse<PageResult<TicketApprovalTemplateVO>> page(
            @Min(value = 1, message = "pageNo must be >= 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "ticketType", required = false) String ticketType,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        PageResult<TicketApprovalTemplate> page = ticketApprovalAdminService.pageTemplates(TicketApprovalTemplatePageQueryDTO.builder()
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
