package com.smartticket.api.controller;

import com.smartticket.api.assembler.P1ConfigAssembler;
import com.smartticket.api.dto.p1.TicketQueueMemberRequest;
import com.smartticket.api.dto.p1.UpdateEnabledRequest;
import com.smartticket.api.vo.p1.TicketQueueMemberVO;
import com.smartticket.auth.model.AuthUser;
import com.smartticket.biz.dto.TicketQueueMemberCommandDTO;
import com.smartticket.biz.model.CurrentUser;
import com.smartticket.biz.service.TicketQueueMemberService;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.domain.entity.TicketQueueMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ticket-queues/{queueId}/members")
@Tag(name = "P1 队列成员配置", description = "队列成员添加、启停和查询")
public class TicketQueueMemberController {
    private final TicketQueueMemberService ticketQueueMemberService;
    private final P1ConfigAssembler assembler;

    public TicketQueueMemberController(TicketQueueMemberService ticketQueueMemberService, P1ConfigAssembler assembler) {
        this.ticketQueueMemberService = ticketQueueMemberService;
        this.assembler = assembler;
    }

    @PostMapping
    @Operation(summary = "添加队列成员")
    public ApiResponse<TicketQueueMemberVO> create(
            Authentication authentication,
            @PathVariable("queueId") Long queueId,
            @Valid @RequestBody TicketQueueMemberRequest request
    ) {
        TicketQueueMember member = ticketQueueMemberService.create(currentUser(authentication), queueId, toCommand(request));
        return ApiResponse.success(assembler.toQueueMemberVO(member));
    }

    @PatchMapping("/{memberId}/enabled")
    @Operation(summary = "启用或停用队列成员")
    public ApiResponse<TicketQueueMemberVO> updateEnabled(
            Authentication authentication,
            @PathVariable("queueId") Long queueId,
            @PathVariable("memberId") Long memberId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketQueueMember member = ticketQueueMemberService.updateEnabled(currentUser(authentication), queueId, memberId, request.getEnabled());
        return ApiResponse.success(assembler.toQueueMemberVO(member));
    }

    @GetMapping
    @Operation(summary = "查询队列成员")
    public ApiResponse<List<TicketQueueMemberVO>> list(
            @PathVariable("queueId") Long queueId,
            @RequestParam(value = "enabled", required = false) Boolean enabled
    ) {
        return ApiResponse.success(ticketQueueMemberService.list(queueId, enabled)
                .stream()
                .map(assembler::toQueueMemberVO)
                .toList());
    }

    private TicketQueueMemberCommandDTO toCommand(TicketQueueMemberRequest request) {
        return TicketQueueMemberCommandDTO.builder()
                .userId(request.getUserId())
                .enabled(request.getEnabled())
                .build();
    }

    private CurrentUser currentUser(Authentication authentication) {
        AuthUser authUser = (AuthUser) authentication.getPrincipal();
        return CurrentUser.builder()
                .userId(authUser.getUserId())
                .username(authUser.getUsername())
                .roles(authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(authority -> authority.replace("ROLE_", ""))
                        .toList())
                .build();
    }
}
