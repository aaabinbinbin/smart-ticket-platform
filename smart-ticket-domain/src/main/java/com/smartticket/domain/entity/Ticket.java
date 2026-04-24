package com.smartticket.domain.entity;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import com.smartticket.domain.enums.TicketTypeEnum;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单类。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    // ID
    private Long id;
    // 工单编号
    private String ticketNo;
    // 标题
    private String title;
    // 描述
    private String description;
    // 类型
    private TicketTypeEnum type;
    // 分类
    private TicketCategoryEnum category;
    // 优先级
    private TicketPriorityEnum priority;
    // 状态
    private TicketStatusEnum status;
    // 创建人ID
    private Long creatorId;
    // 处理人ID
    private Long assigneeId;
    // 分组ID
    private Long groupId;
    // 队列ID
    private Long queueId;
    // 解决摘要
    private String solutionSummary;
    // 来源
    private String source;
    // 幂等键
    private String idempotencyKey;
    // 已创建时间
    private LocalDateTime createdAt;
    // 已更新时间
    private LocalDateTime updatedAt;
    // 类型画像
    private Map<String, Object> typeProfile;
}
