package com.smartticket.api.controller.assignment;

import com.smartticket.api.assembler.config.P1ConfigAssembler;
import com.smartticket.api.dto.assignment.TicketQueueMemberRequest;
import com.smartticket.api.dto.common.UpdateEnabledRequest;
import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.vo.assignment.TicketQueueMemberVO;
import com.smartticket.biz.dto.assignment.TicketQueueMemberCommandDTO;
import com.smartticket.biz.service.assignment.TicketQueueMemberService;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.domain.entity.TicketQueueMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单队列成员控制器。
 */
@RestController
@RequestMapping("/api/ticket-queues/{queueId}/members")
@Tag(name = "队列成员", description = "队列成员管理")
public class TicketQueueMemberController {
    // 工单队列成员服务
    private final TicketQueueMemberService ticketQueueMemberService;
    // 当前用户解析器
    private final CurrentUserResolver currentUserResolver;
    // 装配器
    private final P1ConfigAssembler assembler;

    /**
     * 构造工单队列成员控制器。
     */
    public TicketQueueMemberController(
            TicketQueueMemberService ticketQueueMemberService,
            CurrentUserResolver currentUserResolver,
            P1ConfigAssembler assembler
    ) {
        this.ticketQueueMemberService = ticketQueueMemberService;
        this.currentUserResolver = currentUserResolver;
        this.assembler = assembler;
    }

    /**
     * 创建。
     */
    @PostMapping
    @Operation(summary = "新增队列成员")
    public ApiResponse<TicketQueueMemberVO> create(
            Authentication authentication,
            @PathVariable("queueId") Long queueId,
            @Valid @RequestBody TicketQueueMemberRequest request
    ) {
        TicketQueueMember member = ticketQueueMemberService.create(currentUserResolver.resolve(authentication), queueId, toCommand(request));
        return ApiResponse.success(assembler.toQueueMemberVO(member));
    }

    /**
     * 更新启用。
     */
    @PatchMapping("/{memberId}/enabled")
    @Operation(summary = "启用或停用队列成员")
    public ApiResponse<TicketQueueMemberVO> updateEnabled(
            Authentication authentication,
            @PathVariable("queueId") Long queueId,
            @PathVariable("memberId") Long memberId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketQueueMember member = ticketQueueMemberService.updateEnabled(currentUserResolver.resolve(authentication), queueId, memberId, request.getEnabled());
        return ApiResponse.success(assembler.toQueueMemberVO(member));
    }

    /**
     * 处理列表。
     */
    @GetMapping
    @Operation(summary = "查询队列成员列表")
    public ApiResponse<List<TicketQueueMemberVO>> list(
            @PathVariable("queueId") Long queueId,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        return ApiResponse.success(ticketQueueMemberService.list(queueId, enabled)
                .stream()
                .map(assembler::toQueueMemberVO)
                .toList());
    }

    /**
     * 转换为命令。
     */
    private TicketQueueMemberCommandDTO toCommand(TicketQueueMemberRequest request) {
        return TicketQueueMemberCommandDTO.builder()
                .userId(request.getUserId())
                .enabled(request.getEnabled())
                .build();
    }

}
