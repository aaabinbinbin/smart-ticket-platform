package com.smartticket.domain.entity;

import com.smartticket.domain.enums.TicketCategoryEnum;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单主实体，对应表 {@code ticket}。
 *
 * <p>该实体保存工单当前事实状态，例如当前状态、当前处理人、优先级和分类。
 * 工单评论、操作日志、附件等过程数据不放在该实体中。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    /** 工单主键。 */
    private Long id;
    /** 业务工单号，例如 INC202604170001。 */
    private String ticketNo;
    /** 工单标题。 */
    private String title;
    /** 问题描述。 */
    private String description;
    /** 工单分类。 */
    private TicketCategoryEnum category;
    /** 工单优先级。 */
    private TicketPriorityEnum priority;
    /** 工单当前状态。 */
    private TicketStatusEnum status;
    /** 提单人用户 ID。 */
    private Long creatorId;
    /** 当前处理人用户 ID，待分配时可为空。 */
    private Long assigneeId;
    /** 当前绑定的工单组 ID，可为空。 */
    private Long groupId;
    /** 当前绑定的工单队列 ID，可为空。 */
    private Long queueId;
    /** 解决方案摘要，通常在解决或关闭阶段填写。 */
    private String solutionSummary;
    /** 创建来源：MANUAL-手工创建，AGENT-Agent 创建。 */
    private String source;
    /** 创建幂等键，用于防止重复提交。 */
    private String idempotencyKey;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
