package com.smartticket.api.controller.notification;

import com.smartticket.api.support.CurrentUserResolver;
import com.smartticket.api.vo.notification.TicketNotificationVO;
import com.smartticket.biz.dto.notification.TicketNotificationPageQueryDTO;
import com.smartticket.biz.service.notification.TicketNotificationService;
import com.smartticket.common.response.ApiResponse;
import com.smartticket.common.response.PageResult;
import com.smartticket.domain.entity.TicketNotification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notification APIs")
public class TicketNotificationController {
    private final TicketNotificationService ticketNotificationService;
    private final CurrentUserResolver currentUserResolver;

    public TicketNotificationController(
            TicketNotificationService ticketNotificationService,
            CurrentUserResolver currentUserResolver
    ) {
        this.ticketNotificationService = ticketNotificationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    @Operation(summary = "Page my notifications")
    public ApiResponse<PageResult<TicketNotificationVO>> page(
            Authentication authentication,
            @Min(value = 1, message = "pageNo must be >= 1")
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly
    ) {
        PageResult<TicketNotification> page = ticketNotificationService.pageMyNotifications(
                currentUserResolver.resolve(authentication),
                TicketNotificationPageQueryDTO.builder()
                        .pageNo(pageNo)
                        .pageSize(pageSize)
                        .unreadOnly(unreadOnly)
                        .build()
        );
        return ApiResponse.success(PageResult.<TicketNotificationVO>builder()
                .pageNo(page.getPageNo())
                .pageSize(page.getPageSize())
                .total(page.getTotal())
                .records(page.getRecords().stream().map(this::toVO).toList())
                .build());
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ApiResponse<TicketNotificationVO> markRead(
            Authentication authentication,
            @PathVariable("notificationId") Long notificationId
    ) {
        return ApiResponse.success(toVO(ticketNotificationService.markRead(
                currentUserResolver.resolve(authentication),
                notificationId
        )));
    }

    private TicketNotificationVO toVO(TicketNotification notification) {
        if (notification == null) {
            return null;
        }
        return TicketNotificationVO.builder()
                .id(notification.getId())
                .ticketId(notification.getTicketId())
                .receiverUserId(notification.getReceiverUserId())
                .channel(notification.getChannel())
                .notificationType(notification.getNotificationType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .read(Integer.valueOf(1).equals(notification.getReadStatus()))
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}
