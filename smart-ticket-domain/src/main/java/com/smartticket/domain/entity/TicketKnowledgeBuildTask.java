package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单知识构建任务实体，用于保证工单关闭后入知识库流程可重试、可补偿。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketKnowledgeBuildTask {
    /**
     * 任务主键 ID。
     */
    private Long id;

    /**
     * 来源工单 ID。
     */
    private Long ticketId;

    /**
     * 任务类型，例如 CLOSED_TICKET。
     */
    private String taskType;

    /**
     * 任务状态，例如 PENDING、PROCESSING、SUCCESS、FAILED、DEAD。
     */
    private String status;

    /**
     * 已失败重试次数。
     */
    private Integer retryCount;

    /**
     * 下一次允许重试的时间。
     */
    private LocalDateTime nextRetryAt;

    /**
     * 最近一次失败原因。
     */
    private String lastError;

    /**
     * 当前抢占该任务的 worker 标识。
     */
    private String lockedBy;

    /**
     * 当前任务被抢占的时间。
     */
    private LocalDateTime lockedAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
