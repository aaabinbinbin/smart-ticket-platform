package com.smartticket.biz.dto.assignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列成员命令DTO数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueMemberCommandDTO {
    // 用户ID
    private Long userId;
    // 启用
    private Boolean enabled;
}

