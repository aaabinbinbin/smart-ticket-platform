package com.smartticket.api.controller.sla;

import com.smartticket.api.assembler.config.P1ConfigAssembler;
import com.smartticket.api.dto.common.UpdateEnabledRequest;
import com.smartticket.api.dto.sla.TicketSlaPolicyRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.vo.sla.TicketSlaInstanceVO;
import com.smartticket.api.vo.sla.TicketSlaPolicyVO;
import com.smartticket.api.vo.sla.TicketSlaScanResultVO;
import com.smartticket.biz.dto.sla.TicketSlaPolicyCommandDTO;
import com.smartticket.biz.dto.sla.TicketSlaPolicyPageQueryDTO;
import com.smartticket.biz.service.sla.TicketSlaService;
import com.smartticket.biz.service.ticket.TicketQueryService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketSlaPolicy;
import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api")
@Tag(name = "SLA Policies", description = "SLA policy and instance APIs")
public class TicketSlaController {
    private final TicketSlaService ticketSlaService;
    private final TicketQueryService ticketQueryService;
    private final CurrentUserResolver currentUserResolver;
    private final P1ConfigAssembler assembler;

    public TicketSlaController(
            TicketSlaService ticketSlaService,
            TicketQueryService ticketQueryService,
            CurrentUserResolver currentUserResolver,
            P1ConfigAssembler assembler
    ) {
        this.ticketSlaService = ticketSlaService;
        this.ticketQueryService = ticketQueryService;
        this.currentUserResolver = currentUserResolver;
        this.assembler = assembler;
    }

    @PostMapping("/ticket-sla-policies")
    @Operation(summary = "Create SLA policy", description = "Admin only")
    public ApiResponse<TicketSlaPolicyVO> createPolicy(
            Authentication authentication,
            @Valid @RequestBody TicketSlaPolicyRequest request
    ) {
        TicketSlaPolicy policy = ticketSlaService.createPolicy(currentUserResolver.resolve(authentication), toCommand(request));
        return ApiResponse.success(assembler.toSlaPolicyVO(policy));
    }

    @PutMapping("/ticket-sla-policies/{policyId}")
    @Operation(summary = "Update SLA policy", description = "Admin only")
    public ApiResponse<TicketSlaPolicyVO> updatePolicy(
            Authentication authentication,
            @PathVariable("policyId") Long policyId,
            @Valid @RequestBody TicketSlaPolicyRequest request
    ) {
        TicketSlaPolicy policy = ticketSlaService.updatePolicy(currentUserResolver.resolve(authentication), policyId, toCommand(request));
        return ApiResponse.success(assembler.toSlaPolicyVO(policy));
    }

    @PatchMapping("/ticket-sla-policies/{policyId}/enabled")
    @Operation(summary = "Enable or disable SLA policy", description = "Admin only")
    public ApiResponse<TicketSlaPolicyVO> updatePolicyEnabled(
            Authentication authentication,
            @PathVariable("policyId") Long policyId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketSlaPolicy policy = ticketSlaService.updatePolicyEnabled(currentUserResolver.resolve(authentication), policyId, request.getEnabled());
        return ApiResponse.success(assembler.toSlaPolicyVO(policy));
    }

    @GetMapping("/ticket-sla-policies/{policyId}")
    @Operation(summary = "Get SLA policy")
    public ApiResponse<TicketSlaPolicyVO> getPolicy(@PathVariable("policyId") Long policyId) {
        return ApiResponse.success(assembler.toSlaPolicyVO(ticketSlaService.getPolicy(policyId)));
    }

    @GetMapping("/ticket-sla-policies")
    @Operation(summary = "Page SLA policies")
    public ApiResponse<PageResult<TicketSlaPolicyVO>> pagePolicies(
            @Min(value = 1, message = "pageNo must be >= 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        PageResult<TicketSlaPolicy> page = ticketSlaService.pagePolicies(TicketSlaPolicyPageQueryDTO.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .category(parseCategory(category))
                .priority(parsePriority(priority))
                .enabled(enabled)
                .build());
        return ApiResponse.success(PageResult.<TicketSlaPolicyVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(assembler::toSlaPolicyVO).toList())
                .build());
    }

    @GetMapping("/tickets/{ticketId}/sla")
    @Operation(summary = "Get ticket SLA instance")
    public ApiResponse<TicketSlaInstanceVO> getTicketSla(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        ticketQueryService.getDetail(currentUserResolver.resolve(authentication), ticketId);
        return ApiResponse.success(assembler.toSlaInstanceVO(ticketSlaService.getInstanceByTicketId(ticketId)));
    }

    @PostMapping("/ticket-sla-instances/breach-scan")
    @Operation(summary = "Scan breached SLA instances", description = "Admin only")
    public ApiResponse<TicketSlaScanResultVO> scanBreachedInstances(
            Authentication authentication,
            @Min(value = 1, message = "limit must be >= 1")
            @Max(value = 1000, message = "limit must be <= 1000")
            @RequestParam(value = "limit", defaultValue = "100") Integer limit
    ) {
        return ApiResponse.success(assembler.toSlaScanResultVO(
                ticketSlaService.scanBreachedInstances(currentUserResolver.resolve(authentication), limit)
        ));
    }

    private TicketSlaPolicyCommandDTO toCommand(TicketSlaPolicyRequest request) {
        return TicketSlaPolicyCommandDTO.builder()
                .policyName(request.getPolicyName())
                .category(parseCategory(request.getCategory()))
                .priority(parsePriority(request.getPriority()))
                .firstResponseMinutes(request.getFirstResponseMinutes())
                .resolveMinutes(request.getResolveMinutes())
                .enabled(request.getEnabled())
                .build();
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
}
