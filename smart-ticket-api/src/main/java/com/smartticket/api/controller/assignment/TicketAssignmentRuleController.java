package com.smartticket.api.controller.assignment;

import com.smartticket.api.assembler.config.P1ConfigAssembler;
import com.smartticket.api.assembler.ticket.TicketAssembler;
import com.smartticket.api.dto.assignment.TicketAssignmentRuleRequest;
import com.smartticket.api.dto.common.UpdateEnabledRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.vo.assignment.TicketAssignmentPreviewVO;
import com.smartticket.api.vo.assignment.TicketAssignmentRuleVO;
import com.smartticket.api.vo.assignment.TicketAssignmentStatsVO;
import com.smartticket.api.vo.ticket.TicketVO;
import com.smartticket.biz.dto.assignment.TicketAssignmentRuleCommandDTO;
import com.smartticket.biz.dto.assignment.TicketAssignmentRulePageQueryDTO;
import com.smartticket.biz.service.assignment.TicketAssignmentService;
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
 * 工单分派规则控制器。
 */
@Validated
@RestController
@RequestMapping("/api")
@Tag(name = "分派规则", description = "自动分派规则管理")
public class TicketAssignmentRuleController {
    // 工单Assignment服务
    private final TicketAssignmentService ticketAssignmentService;
    // 当前用户解析器
    private final CurrentUserResolver currentUserResolver;
    // 装配器
    private final P1ConfigAssembler assembler;
    // 工单装配器
    private final TicketAssembler ticketAssembler;

    /**
     * 构造工单分派规则控制器。
     */
    public TicketAssignmentRuleController(
            TicketAssignmentService ticketAssignmentService,
            CurrentUserResolver currentUserResolver,
            P1ConfigAssembler assembler,
            TicketAssembler ticketAssembler
    ) {
        this.ticketAssignmentService = ticketAssignmentService;
        this.currentUserResolver = currentUserResolver;
        this.assembler = assembler;
        this.ticketAssembler = ticketAssembler;
    }

    /**
     * 创建。
     */
    @PostMapping("/ticket-assignment-rules")
    @Operation(summary = "创建分派规则", description = "仅管理员可操作")
    public ApiResponse<TicketAssignmentRuleVO> create(
            Authentication authentication,
            @Valid @RequestBody TicketAssignmentRuleRequest request
    ) {
        TicketAssignmentRule rule = ticketAssignmentService.createRule(currentUserResolver.resolve(authentication), toCommand(request));
        return ApiResponse.success(assembler.toAssignmentRuleVO(rule));
    }

    /**
     * 更新。
     */
    @PutMapping("/ticket-assignment-rules/{ruleId}")
    @Operation(summary = "更新分派规则", description = "仅管理员可操作")
    public ApiResponse<TicketAssignmentRuleVO> update(
            Authentication authentication,
            @PathVariable("ruleId") Long ruleId,
            @Valid @RequestBody TicketAssignmentRuleRequest request
    ) {
        TicketAssignmentRule rule = ticketAssignmentService.updateRule(currentUserResolver.resolve(authentication), ruleId, toCommand(request));
        return ApiResponse.success(assembler.toAssignmentRuleVO(rule));
    }

    /**
     * 更新启用。
     */
    @PatchMapping("/ticket-assignment-rules/{ruleId}/enabled")
    @Operation(summary = "启用或停用分派规则", description = "仅管理员可操作")
    public ApiResponse<TicketAssignmentRuleVO> updateEnabled(
            Authentication authentication,
            @PathVariable("ruleId") Long ruleId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketAssignmentRule rule = ticketAssignmentService.updateRuleEnabled(currentUserResolver.resolve(authentication), ruleId, request.getEnabled());
        return ApiResponse.success(assembler.toAssignmentRuleVO(rule));
    }

    /**
     * 获取详情。
     */
    @GetMapping("/ticket-assignment-rules/{ruleId}")
    @Operation(summary = "获取分派规则")
    public ApiResponse<TicketAssignmentRuleVO> get(@PathVariable("ruleId") Long ruleId) {
        return ApiResponse.success(assembler.toAssignmentRuleVO(ticketAssignmentService.getRule(ruleId)));
    }

    /**
     * 分页查询。
     */
    @GetMapping("/ticket-assignment-rules")
    @Operation(summary = "分页查询分派规则")
    public ApiResponse<PageResult<TicketAssignmentRuleVO>> page(
            @Min(value = 1, message = "pageNo must be >= 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        PageResult<TicketAssignmentRule> page = ticketAssignmentService.pageRules(TicketAssignmentRulePageQueryDTO.builder()
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

    /**
     * 获取统计信息。
     */
    @GetMapping("/ticket-assignment-rules/stats")
    @Operation(summary = "获取分派统计")
    public ApiResponse<TicketAssignmentStatsVO> stats() {
        return ApiResponse.success(assembler.toAssignmentStatsVO(ticketAssignmentService.stats()));
    }

    /**
     * 预览分派结果。
     */
    @PostMapping("/tickets/{ticketId}/assignment-preview")
    @Operation(summary = "预览分派结果", description = "仅预览，不执行写入")
    public ApiResponse<TicketAssignmentPreviewVO> preview(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        return ApiResponse.success(assembler.toAssignmentPreviewVO(ticketAssignmentService.preview(currentUserResolver.resolve(authentication), ticketId)));
    }

    /**
     * 执行分派。
     */
    @PostMapping("/tickets/{ticketId}/auto-assign")
    @Operation(summary = "自动分派工单", description = "执行分派规则并写入结果")
    public ApiResponse<TicketVO> autoAssign(
            Authentication authentication,
            @PathVariable("ticketId") Long ticketId
    ) {
        return ApiResponse.success(ticketAssembler.toVO(ticketAssignmentService.autoAssign(currentUserResolver.resolve(authentication), ticketId)));
    }

    /**
     * 转换为命令。
     */
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

    /**
     * 解析分类。
     */
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

    /**
     * 解析优先级。
     */
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
