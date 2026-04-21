package com.smartticket.biz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单队列写入命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketQueueCommandDTO {
    /** 队列名称。 */
    private String queueName;
    /** 队列编码，创建后不允许修改。 */
    private String queueCode;
    /** 所属工单组 ID。 */
    private Long groupId;
    /** 是否启用，空值按启用处理。 */
    private Boolean enabled;
}
