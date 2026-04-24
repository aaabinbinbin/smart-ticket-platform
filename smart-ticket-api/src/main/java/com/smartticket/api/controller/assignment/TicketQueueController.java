package com.smartticket.api.controller.assignment;

import com.smartticket.api.assembler.config.P1ConfigAssembler;
import com.smartticket.api.dto.assignment.TicketQueueRequest;
import com.smartticket.api.dto.common.UpdateEnabledRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.vo.assignment.TicketQueueVO;
import com.smartticket.biz.dto.assignment.TicketQueueCommandDTO;
import com.smartticket.biz.dto.assignment.TicketQueuePageQueryDTO;
import com.smartticket.biz.service.assignment.TicketQueueService;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketQueue;
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
 * 工单队列控制器。
 */
@Validated
@RestController
@RequestMapping("/api/ticket-queues")
@Tag(name = "工单队列", description = "工单队列管理")
public class TicketQueueController {
    // 工单队列服务
    private final TicketQueueService ticketQueueService;
    // 当前用户解析器
    private final CurrentUserResolver currentUserResolver;
    // 装配器
    private final P1ConfigAssembler assembler;

    /**
     * 构造工单队列控制器。
     */
    public TicketQueueController(
            TicketQueueService ticketQueueService,
            CurrentUserResolver currentUserResolver,
            P1ConfigAssembler assembler
    ) {
        this.ticketQueueService = ticketQueueService;
        this.currentUserResolver = currentUserResolver;
        this.assembler = assembler;
    }

    /**
     * 创建。
     */
    @PostMapping
    @Operation(summary = "创建工单队列", description = "仅管理员可操作")
    public ApiResponse<TicketQueueVO> create(
            Authentication authentication,
            @Valid @RequestBody TicketQueueRequest request
    ) {
        TicketQueue queue = ticketQueueService.create(currentUserResolver.resolve(authentication), toCommand(request));
        return ApiResponse.success(assembler.toQueueVO(queue));
    }

    /**
     * 更新。
     */
    @PutMapping("/{queueId}")
    @Operation(summary = "更新工单队列", description = "仅管理员可操作")
    public ApiResponse<TicketQueueVO> update(
            Authentication authentication,
            @PathVariable("queueId") Long queueId,
            @Valid @RequestBody TicketQueueRequest request
    ) {
        TicketQueue queue = ticketQueueService.update(currentUserResolver.resolve(authentication), queueId, toCommand(request));
        return ApiResponse.success(assembler.toQueueVO(queue));
    }

    /**
     * 更新启用。
     */
    @PatchMapping("/{queueId}/enabled")
    @Operation(summary = "启用或停用工单队列", description = "仅管理员可操作")
    public ApiResponse<TicketQueueVO> updateEnabled(
            Authentication authentication,
            @PathVariable("queueId") Long queueId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketQueue queue = ticketQueueService.updateEnabled(currentUserResolver.resolve(authentication), queueId, request.getEnabled());
        return ApiResponse.success(assembler.toQueueVO(queue));
    }

    /**
     * 获取详情。
     */
    @GetMapping("/{queueId}")
    @Operation(summary = "获取工单队列")
    public ApiResponse<TicketQueueVO> get(@PathVariable("queueId") Long queueId) {
        return ApiResponse.success(assembler.toQueueVO(ticketQueueService.get(queueId)));
    }

    /**
     * 分页查询。
     */
    @GetMapping
    @Operation(summary = "分页查询工单队列")
    public ApiResponse<PageResult<TicketQueueVO>> page(
            @Min(value = 1, message = "pageNo must be >= 1") @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        PageResult<TicketQueue> page = ticketQueueService.page(TicketQueuePageQueryDTO.builder()
                .pageNo(pageNo)
                .pageSize(pageSize)
                .groupId(groupId)
                .keyword(keyword)
                .enabled(enabled)
                .build());
        return ApiResponse.success(PageResult.<TicketQueueVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(assembler::toQueueVO).toList())
                .build());
    }

    /**
     * 转换为命令。
     */
    private TicketQueueCommandDTO toCommand(TicketQueueRequest request) {
        return TicketQueueCommandDTO.builder()
                .queueName(request.getQueueName())
                .queueCode(request.getQueueCode())
                .groupId(request.getGroupId())
                .enabled(request.getEnabled())
                .build();
    }

}
