package com.smartticket.biz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单组写入命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGroupCommandDTO {
    /** 工单组名称。 */
    private String groupName;
    /** 工单组编码，创建后不允许修改。 */
    private String groupCode;
    /** 组负责人用户 ID，可为空。 */
    private Long ownerUserId;
    /** 是否启用，空值按启用处理。 */
    private Boolean enabled;
}
