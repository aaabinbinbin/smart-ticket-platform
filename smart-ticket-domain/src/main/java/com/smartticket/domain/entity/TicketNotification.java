package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单通知类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketNotification {
    // ID
    private Long id;
    // 工单ID
    private Long ticketId;
    // receiver用户ID
    private Long receiverUserId;
    // channel
    private String channel;
    // 通知类型
    private String notificationType;
    // 标题
    private String title;
    // 内容
    private String content;
    // read状态
    private Integer readStatus;
    // read时间
    private LocalDateTime readAt;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
