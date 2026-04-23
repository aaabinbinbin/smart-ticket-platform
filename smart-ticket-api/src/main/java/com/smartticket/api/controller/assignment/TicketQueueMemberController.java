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

@RestController
@RequestMapping("/api/ticket-queues/{queueId}/members")
@Tag(name = "Queue Members", description = "Queue member management")
public class TicketQueueMemberController {
    private final TicketQueueMemberService ticketQueueMemberService;
    private final CurrentUserResolver currentUserResolver;
    private final P1ConfigAssembler assembler;

    public TicketQueueMemberController(
            TicketQueueMemberService ticketQueueMemberService,
            CurrentUserResolver currentUserResolver,
            P1ConfigAssembler assembler
    ) {
        this.ticketQueueMemberService = ticketQueueMemberService;
        this.currentUserResolver = currentUserResolver;
        this.assembler = assembler;
    }

    @PostMapping
    @Operation(summary = "Add queue member")
    public ApiResponse<TicketQueueMemberVO> create(
            Authentication authentication,
            @PathVariable("queueId") Long queueId,
            @Valid @RequestBody TicketQueueMemberRequest request
    ) {
        TicketQueueMember member = ticketQueueMemberService.create(currentUserResolver.resolve(authentication), queueId, toCommand(request));
        return ApiResponse.success(assembler.toQueueMemberVO(member));
    }

    @PatchMapping("/{memberId}/enabled")
    @Operation(summary = "Enable or disable queue member")
    public ApiResponse<TicketQueueMemberVO> updateEnabled(
            Authentication authentication,
            @PathVariable("queueId") Long queueId,
            @PathVariable("memberId") Long memberId,
            @Valid @RequestBody UpdateEnabledRequest request
    ) {
        TicketQueueMember member = ticketQueueMemberService.updateEnabled(currentUserResolver.resolve(authentication), queueId, memberId, request.getEnabled());
        return ApiResponse.success(assembler.toQueueMemberVO(member));
    }

    @GetMapping
    @Operation(summary = "List queue members")
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

}
