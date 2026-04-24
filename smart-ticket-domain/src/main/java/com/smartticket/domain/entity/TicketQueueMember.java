package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列成员类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMember {
    // ID
    private Long id;
    // 队列ID
    private Long queueId;
    // 用户ID
    private Long userId;
    // 启用
    private Integer enabled;
    // lastAssigned时间
    private LocalDateTime lastAssignedAt;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
