package com.smartticket.api.controller;

import com.smartticket.api.assembler.P1ConfigAssembler;
import com.smartticket.api.assembler.TicketAssembler;
import com.smartticket.api.dto.p1.TicketAssignmentRuleRequest;
import com.smartticket.api.dto.p1.UpdateEnabledRequest;
import com.smartticket.api.vo.p1.TicketAssignmentPreviewVO;
import com.smartticket.api.vo.p1.TicketAssignmentRuleVO;
import com.smartticket.api.vo.p1.TicketAssignmentStatsVO;
import com.smartticket.api.vo.ticket.TicketVO;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.dto.TicketAssignmentRuleCommandDTO;
import com.smartticket.biz.dto.TicketAssignmentRulePageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.TicketAssignmentRuleService;
import com.smartticket.common.exception.BusinessErrorCode;
import com.smartticket.common.exception.BusinessException;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketAssignmentRule;
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

@Validated
@RestController
@RequestMapping("/api")
@Tag(name = "P1 自动分派", description = "自动分派规则管理、预览、执行与统计")
public class TicketAssignmentRuleController {
    private final TicketAssignmentRuleService assignmentRuleService;
    private final P1ConfigAssembler assembler;
    private final TicketAssembler ticketAssembler;

    public TicketAssignmentRuleController(
            TicketAssignmentRuleService assignmentRuleService,
            P1ConfigAssembler assembler,
            TicketAssembler ticketAssembler
    ) {
        this.assignmentRuleService = assignmentRuleService;
        this.assembler = assembler;
        this.ticketAssembler = ticketAssembler;
    }

    @PostMapping("/ticket-assignment-rules")
    @Operation(summary = "创建自动分派规则", description = "仅 ADMIN 可操作")
    public ApiResponse<TicketAssignmentRuleVO> create(
            Authentication authentication,
            @Valid @RequestBody TicketAssignmentRuleRequest request
    ) {
        TicketAssignmentRule rule = assignmentRuleService.create(currentUser(authentication), toCommand(request));
        return ApiResponse.success(assembler.toAssignmentRuleVO(rule));
    }

    @PutMapping("/ticket-assignment-rules/{ruleId}")
    @Operation(summary = "更新自动分派规则", description = "仅 ADMIN 可操作")
    public ApiResponse<TicketAssignmentRuleVO> update(
            Authentication authentication,
            @PathVariable("ruleId") Long ruleId,
            @Valid @RequestBody TicketAssignmentRuleRequest request
    ) {
        TicketAssignmentRule rule = assignmentRuleService.update(currentUser(authentication), ruleId, toCommand(request));
        return ApiResponse.success(assembler.toAssignmentRuleVO(rule));
    }

    @PatchMapping("/ticket-assignment-rules/{ruleId}/enabled")
    @Operation(summary = "启用或停用自动分派规则", description = "仅 ADMIN 可操作")
    public ApiResponse<TicketAssignmentRuleVO> updateEnabled(
            Authentication authentication,
            @PathVariable("ruleId") Long ruleId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketAssignmentRule rule = assignmentRuleService.updateEnabled(
                currentUser(authentication),
                ruleId,
                request.getEnabled()
        );
        return ApiResponse.success(assembler.toAssignmentRuleVO(rule));
    }

    @GetMapping("/ticket-assignment-rules/{ruleId}")
    @Operation(summary = "查询自动分派规则详情")
    public ApiResponse<TicketAssignmentRuleVO> get(@PathVariable("ruleId") Long ruleId) {
        return ApiResponse.success(assembler.toAssignmentRuleVO(assignmentRuleService.get(ruleId)));
    }

    @GetMapping("/ticket-assignment-rules")
    @Operation(summary = "分页查询自动分派规则")
    public ApiResponse<PageResult<TicketAssignmentRuleVO>> page(
            @Min(value = 1, message = "页码不能小于 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "每页大小不能小于 1")
            @Max(value = 100, message = "每页大小不能超过 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        PageResult<TicketAssignmentRule> page = assignmentRuleService.page(TicketAssignmentRulePageQueryDTO.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .category(parseCategory(category))
                .priority(parsePriority(priority))
                .enabled(enabled)
                .build());
        return ApiResponse.success(PageResult.<TicketAssignmentRuleVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(assembler::toAssignmentRuleVO).toList())
                .build());
    }

    @GetMapping("/ticket-assignment-rules/stats")
    @Operation(summary = "查询自动分派统计")
    public ApiResponse<TicketAssignmentStatsVO> stats() {
        return ApiResponse.success(assembler.toAssignmentStatsVO(assignmentRuleService.stats()));
    }

    @PostMapping("/tickets/{ticketId}/assignment-preview")
    @Operation(summary = "预览工单自动分派结果", description = "只返回推荐目标，不执行真实分派")
    public ApiResponse<TicketAssignmentPreviewVO> preview(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        return ApiResponse.success(assembler.toAssignmentPreviewVO(
                assignmentRuleService.preview(currentUser(authentication), ticketId)
        ));
    }

    @PostMapping("/tickets/{ticketId}/auto-assign")
    @Operation(summary = "按规则自动分派工单", description = "执行真实分派，支持队列负载均衡和组负责人回退")
    public ApiResponse<TicketVO> autoAssign(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        return ApiResponse.success(ticketAssembler.toVO(
                assignmentRuleService.autoAssign(currentUser(authentication), ticketId)
        ));
    }

    private TicketAssignmentRuleCommandDTO toCommand(TicketAssignmentRuleRequest request) {
        return TicketAssignmentRuleCommandDTO.builder()
                .ruleName(request.getRuleName())
                .category(parseCategory(request.getCategory()))
                .priority(parsePriority(request.getPriority()))
                .targetGroupId(request.getTargetGroupId())
                .targetQueueId(request.getTargetQueueId())
                .targetUserId(request.getTargetUserId())
                .weight(request.getWeight())
                .enabled(request.getEnabled())
                .build();
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
