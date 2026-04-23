package com.smartticket.api.vo.assignment;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列响应视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueVO {
    /** 队列 ID。 */
    private Long id;
    /** 队列名称。 */
    private String queueName;
    /** 队列编码。 */
    private String queueCode;
    /** 所属工单组 ID。 */
    private Long groupId;
    /** 是否启用。 */
    private Boolean enabled;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
