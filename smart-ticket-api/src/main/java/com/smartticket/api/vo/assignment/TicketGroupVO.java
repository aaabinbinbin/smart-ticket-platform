package com.smartticket.api.vo.assignment;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单组响应视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGroupVO {
    /** 工单组 ID。 */
    private Long id;
    /** 工单组名称。 */
    private String groupName;
    /** 工单组编码。 */
    private String groupCode;
    /** 组长用户 ID。 */
    private Long ownerUserId;
    /** 是否启用。 */
    private Boolean enabled;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
