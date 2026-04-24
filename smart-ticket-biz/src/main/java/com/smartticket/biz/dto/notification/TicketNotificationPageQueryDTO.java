package com.smartticket.biz.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单通知分页查询DTO数据传输对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketNotificationPageQueryDTO {
    // 分页编号
    private Integer pageNo;
    // 分页Size
    private Integer pageSize;
    // unreadOnly
    private Boolean unreadOnly;
}
