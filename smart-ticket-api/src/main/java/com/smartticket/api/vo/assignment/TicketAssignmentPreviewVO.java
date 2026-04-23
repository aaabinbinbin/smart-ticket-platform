package com.smartticket.api.vo.assignment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动分派预览响应视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAssignmentPreviewVO {
    /** 工单 ID。 */
    private Long ticketId;
    /** 是否匹配到规则。 */
    private Boolean matched;
    /** 命中的规则 ID。 */
    private Long ruleId;
    /** 命中的规则名称。 */
    private String ruleName;
    /** 目标工单组 ID。 */
    private Long targetGroupId;
    /** 目标队列 ID。 */
    private Long targetQueueId;
    /** 目标处理人 ID。 */
    private Long targetUserId;
    /** 预览原因说明。 */
    private String reason;
}
