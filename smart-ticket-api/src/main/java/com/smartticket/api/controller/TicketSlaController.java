package com.smartticket.api.controller;

import com.smartticket.api.assembler.P1ConfigAssembler;
import com.smartticket.api.dto.p1.TicketSlaPolicyRequest;
import com.smartticket.api.dto.p1.UpdateEnabledRequest;
import com.smartticket.api.vo.p1.TicketSlaInstanceVO;
import com.smartticket.api.vo.p1.TicketSlaPolicyVO;
import com.smartticket.api.vo.p1.TicketSlaScanResultVO;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.dto.TicketSlaPolicyCommandDTO;
import com.smartticket.biz.dto.TicketSlaPolicyPageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.TicketService;
import com.smartticket.biz.service.TicketSlaService;
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

/**
 * 工单 SLA 配置和实例查询接口。
 *
 * <p>P1 第一版只支持策略配置和工单 SLA 实例查询，不做违约扫描和升级策略。</p>
 */
@Validated
@RestController
@RequestMapping("/api")
@Tag(name = "P1 SLA 配置", description = "SLA 策略管理和工单 SLA 实例查询")
public class TicketSlaController {
    private final TicketSlaService ticketSlaService;
    private final TicketService ticketService;
    private final P1ConfigAssembler assembler;

    public TicketSlaController(
            TicketSlaService ticketSlaService,
            TicketService ticketService,
            P1ConfigAssembler assembler
    ) {
        this.ticketSlaService = ticketSlaService;
        this.ticketService = ticketService;
        this.assembler = assembler;
    }

    @PostMapping("/ticket-sla-policies")
    @Operation(summary = "创建 SLA 策略", description = "仅 ADMIN 可操作")
    public ApiResponse<TicketSlaPolicyVO> createPolicy(
            Authentication authentication,
            @Valid @RequestBody TicketSlaPolicyRequest request
    ) {
        TicketSlaPolicy policy = ticketSlaService.createPolicy(currentUser(authentication), toCommand(request));
        return ApiResponse.success(assembler.toSlaPolicyVO(policy));
    }

    @PutMapping("/ticket-sla-policies/{policyId}")
    @Operation(summary = "更新 SLA 策略", description = "仅 ADMIN 可操作")
    public ApiResponse<TicketSlaPolicyVO> updatePolicy(
            Authentication authentication,
            @PathVariable("policyId") Long policyId,
            @Valid @RequestBody TicketSlaPolicyRequest request
    ) {
        TicketSlaPolicy policy = ticketSlaService.updatePolicy(currentUser(authentication), policyId, toCommand(request));
        return ApiResponse.success(assembler.toSlaPolicyVO(policy));
    }

    @PatchMapping("/ticket-sla-policies/{policyId}/enabled")
    @Operation(summary = "启用或停用 SLA 策略", description = "仅 ADMIN 可操作")
    public ApiResponse<TicketSlaPolicyVO> updatePolicyEnabled(
            Authentication authentication,
            @PathVariable("policyId") Long policyId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketSlaPolicy policy = ticketSlaService.updatePolicyEnabled(
                currentUser(authentication),
                policyId,
                request.getEnabled()
        );
        return ApiResponse.success(assembler.toSlaPolicyVO(policy));
    }

    @GetMapping("/ticket-sla-policies/{policyId}")
    @Operation(summary = "查询 SLA 策略详情")
    public ApiResponse<TicketSlaPolicyVO> getPolicy(@PathVariable("policyId") Long policyId) {
        return ApiResponse.success(assembler.toSlaPolicyVO(ticketSlaService.getPolicy(policyId)));
    }

    @GetMapping("/ticket-sla-policies")
    @Operation(summary = "分页查询 SLA 策略")
    public ApiResponse<PageResult<TicketSlaPolicyVO>> pagePolicies(
            @Min(value = 1, message = "页码不能小于 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "每页大小不能小于 1")
            @Max(value = 100, message = "每页大小不能超过 100")
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
    @Operation(summary = "查询工单 SLA 实例", description = "先复用工单详情权限判断，再返回 SLA 实例")
    public ApiResponse<TicketSlaInstanceVO> getTicketSla(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        ticketService.getDetail(currentUser(authentication), ticketId);
        return ApiResponse.success(assembler.toSlaInstanceVO(ticketSlaService.getInstanceByTicketId(ticketId)));
    }

    /** 将 HTTP 请求转换为 biz 命令。 */
    @PostMapping("/ticket-sla-instances/breach-scan")
    @Operation(summary = "手动扫描 SLA 违约实例", description = "仅 ADMIN 可操作；本接口只标记 breached，不做通知、升级或工单状态流转")
    public ApiResponse<TicketSlaScanResultVO> scanBreachedInstances(
            Authentication authentication,
            @Min(value = 1, message = "扫描数量不能小于 1")
            @Max(value = 1000, message = "扫描数量不能超过 1000")
            @RequestParam(value = "limit", defaultValue = "100") Integer limit
    ) {
        return ApiResponse.success(assembler.toSlaScanResultVO(
                ticketSlaService.scanBreachedInstances(currentUser(authentication), limit)
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

    /** 将认证用户转换为 biz 层用户上下文。 */
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

    /** 解析工单分类。 */
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

    /** 解析工单优先级。 */
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
