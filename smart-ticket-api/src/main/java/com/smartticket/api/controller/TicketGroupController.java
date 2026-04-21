package com.smartticket.api.controller;

import com.smartticket.api.assembler.P1ConfigAssembler;
import com.smartticket.api.dto.p1.TicketGroupRequest;
import com.smartticket.api.dto.p1.UpdateEnabledRequest;
import com.smartticket.api.vo.p1.TicketGroupVO;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.dto.TicketGroupCommandDTO;
import com.smartticket.biz.dto.TicketGroupPageQueryDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.TicketGroupService;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketGroup;
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
 * 工单组配置接口。
 *
 * <p>P1 第一阶段只提供基础配置管理，不改变工单创建、分派和状态流转主流程。</p>
 */
@Validated
@RestController
@RequestMapping("/api/ticket-groups")
@Tag(name = "P1 工单组配置", description = "工单组创建、更新、启停、详情和分页查询")
public class TicketGroupController {
    private final TicketGroupService ticketGroupService;
    private final P1ConfigAssembler assembler;

    public TicketGroupController(TicketGroupService ticketGroupService, P1ConfigAssembler assembler) {
        this.ticketGroupService = ticketGroupService;
        this.assembler = assembler;
    }

    @PostMapping
    @Operation(summary = "创建工单组", description = "仅 ADMIN 可操作；工单组编码创建后不允许修改")
    public ApiResponse<TicketGroupVO> create(
            Authentication authentication,
            @Valid @RequestBody TicketGroupRequest request
    ) {
        TicketGroup group = ticketGroupService.create(currentUser(authentication), toCommand(request));
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "更新工单组", description = "仅 ADMIN 可操作；不修改工单组编码")
    public ApiResponse<TicketGroupVO> update(
            Authentication authentication,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody TicketGroupRequest request
    ) {
        TicketGroup group = ticketGroupService.update(currentUser(authentication), groupId, toCommand(request));
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    @PatchMapping("/{groupId}/enabled")
    @Operation(summary = "启用或停用工单组", description = "仅 ADMIN 可操作")
    public ApiResponse<TicketGroupVO> updateEnabled(
            Authentication authentication,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketGroup group = ticketGroupService.updateEnabled(currentUser(authentication), groupId, request.getEnabled());
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "查询工单组详情")
    public ApiResponse<TicketGroupVO> get(@PathVariable("groupId") Long groupId) {
        return ApiResponse.success(assembler.toGroupVO(ticketGroupService.get(groupId)));
    }

    @GetMapping
    @Operation(summary = "分页查询工单组")
    public ApiResponse<PageResult<TicketGroupVO>> page(
            @Min(value = 1, message = "页码不能小于 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "每页大小不能小于 1")
            @Max(value = 100, message = "每页大小不能超过 100")
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

    /** 将 HTTP 请求转换为 biz 命令。 */
    private TicketGroupCommandDTO toCommand(TicketGroupRequest request) {
        return TicketGroupCommandDTO.builder()
                .groupName(request.getGroupName())
                .groupCode(request.getGroupCode())
                .ownerUserId(request.getOwnerUserId())
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
}
