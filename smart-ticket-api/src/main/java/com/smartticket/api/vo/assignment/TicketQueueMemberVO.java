package com.smartticket.api.vo.assignment;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列成员VO视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMemberVO {
    // ID
    private Long id;
    // 队列ID
    private Long queueId;
    // 用户ID
    private Long userId;
    // 启用
    private Boolean enabled;
    // lastAssigned时间
    private LocalDateTime lastAssignedAt;
    // 创建时间
    private LocalDateTime createdAt;
    // 更新时间
    private LocalDateTime updatedAt;
}
