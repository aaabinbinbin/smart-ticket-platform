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

@Validated
@RestController
@RequestMapping("/api/ticket-groups")
@Tag(name = "Ticket Groups", description = "Ticket group management")
public class TicketGroupController {
    private final TicketGroupService ticketGroupService;
    private final CurrentUserResolver currentUserResolver;
    private final P1ConfigAssembler assembler;

    public TicketGroupController(
            TicketGroupService ticketGroupService,
            CurrentUserResolver currentUserResolver,
            P1ConfigAssembler assembler
    ) {
        this.ticketGroupService = ticketGroupService;
        this.currentUserResolver = currentUserResolver;
        this.assembler = assembler;
    }

    @PostMapping
    @Operation(summary = "Create ticket group", description = "Admin only")
    public ApiResponse<TicketGroupVO> create(
            Authentication authentication,
            @Valid @RequestBody TicketGroupRequest request
    ) {
        TicketGroup group = ticketGroupService.create(currentUserResolver.resolve(authentication), toCommand(request));
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Update ticket group", description = "Admin only")
    public ApiResponse<TicketGroupVO> update(
            Authentication authentication,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody TicketGroupRequest request
    ) {
        TicketGroup group = ticketGroupService.update(currentUserResolver.resolve(authentication), groupId, toCommand(request));
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    @PatchMapping("/{groupId}/enabled")
    @Operation(summary = "Enable or disable ticket group", description = "Admin only")
    public ApiResponse<TicketGroupVO> updateEnabled(
            Authentication authentication,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketGroup group = ticketGroupService.updateEnabled(currentUserResolver.resolve(authentication), groupId, request.getEnabled());
        return ApiResponse.success(assembler.toGroupVO(group));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get ticket group")
    public ApiResponse<TicketGroupVO> get(@PathVariable("groupId") Long groupId) {
        return ApiResponse.success(assembler.toGroupVO(ticketGroupService.get(groupId)));
    }

    @GetMapping
    @Operation(summary = "Page ticket groups")
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

    private TicketGroupCommandDTO toCommand(TicketGroupRequest request) {
        return TicketGroupCommandDTO.builder()
                .groupName(request.getGroupName())
                .groupCode(request.getGroupCode())
                .ownerUserId(request.getOwnerUserId())
                .enabled(request.getEnabled())
                .build();
    }

}
