package com.smartticket.api.vo.notification;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketNotificationVO {
    private Long id;
    private Long ticketId;
    private Long receiverUserId;
    private String channel;
    private String notificationType;
    private String title;
    private String content;
    private Boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
