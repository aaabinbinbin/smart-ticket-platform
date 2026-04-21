package com.smartticket.api.vo.p1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动分派 preview 响应视图。
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
    /** 推荐工单组 ID。 */
    private Long targetGroupId;
    /** 推荐队列 ID。 */
    private Long targetQueueId;
    /** 推荐处理人 ID。 */
    private Long targetUserId;
    /** 推荐原因。 */
    private String reason;
}
