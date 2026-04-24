package com.smartticket.api.controller.assignment;

import com.smartticket.api.assembler.config.P1ConfigAssembler;
import com.smartticket.api.dto.assignment.TicketGroupRequest;
import com.smartticket.api.dto.common.UpdateEnabledRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.vo.assignment.TicketGroupVO;
import com.smartticket.biz.dto.assignment.TicketGroupCommandDTO;
import com.smartticket.biz.dto.assignment.TicketGroupPageQueryDTO;
import com.smartticket.biz.service.assignment.TicketGroupService;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketGroup;
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
 * 工单分组控制器。
 */
@Validated
@RestController
@RequestMapping("/api/ticket-groups")
@Tag(name = "工单分组", description = "工单分组管理")
public class TicketGroupController {
    // 工单分组服务
    private final TicketGroupService ticketGroupService;
    // 当前用户解析器
    private final CurrentUserResolver currentUserResolver;
    // 装配器
    private final P1ConfigAssembler assembler;

    /**
     * 构造工单分组控制器。
     */
    public TicketGroupController(
            TicketGroupService ticketGroupService,
            CurrentUserResolver currentUserResolver,
            P1ConfigAssembler assembler
    ) {
        this.ticketGroupService = ticketGroupService;
        this.currentUserResolver = currentUserResolver;
        this.assembler = assembler;
    }

    /**
     * 创建。
     */
    @PostMapping
    @Operation(summary = "创建工单分组", description = "仅管理员可操作")
    public ApiResponse<TicketGroupVO> create(
            Authentication authentication,
            @Valid @RequestBody TicketGroupRequest request
    ) {
        TicketGroup group = ticketGroupService.create(currentUserResolver.resolve(authentication), toCommand(request));
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    /**
     * 更新。
     */
    @PutMapping("/{groupId}")
    @Operation(summary = "更新工单分组", description = "仅管理员可操作")
    public ApiResponse<TicketGroupVO> update(
            Authentication authentication,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody TicketGroupRequest request
    ) {
        TicketGroup group = ticketGroupService.update(currentUserResolver.resolve(authentication), groupId, toCommand(request));
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    /**
     * 更新启用。
     */
    @PatchMapping("/{groupId}/enabled")
    @Operation(summary = "启用或停用工单分组", description = "仅管理员可操作")
    public ApiResponse<TicketGroupVO> updateEnabled(
            Authentication authentication,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketGroup group = ticketGroupService.updateEnabled(currentUserResolver.resolve(authentication), groupId, request.getEnabled());
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    /**
     * 获取详情。
     */
    @GetMapping("/{groupId}")
    @Operation(summary = "获取工单分组")
    public ApiResponse<TicketGroupVO> get(@PathVariable("groupId") Long groupId) {
        return ApiResponse.success(assembler.toGroupVO(ticketGroupService.get(groupId)));
    }

    /**
     * 分页查询。
     */
    @GetMapping
    @Operation(summary = "分页查询工单分组")
    public ApiResponse<PageResult<TicketGroupVO>> page(
            @Min(value = 1, message = "pageNo must be >= 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        PageResult<TicketGroup> page = ticketGroupService.page(TicketGroupPageQueryDTO.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .keyword(keyword)
                .enabled(enabled)
                .build());
        return ApiResponse.success(PageResult.<TicketGroupVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(assembler::toGroupVO).toList())
                .build());
    }

    /**
     * 转换为命令。
     */
    private TicketGroupCommandDTO toCommand(TicketGroupRequest request) {
        return TicketGroupCommandDTO.builder()
                .groupName(request.getGroupName())
                .groupCode(request.getGroupCode())
                .ownerUserId(request.getOwnerUserId())
                .enabled(request.getEnabled())
                .build();
    }

}
