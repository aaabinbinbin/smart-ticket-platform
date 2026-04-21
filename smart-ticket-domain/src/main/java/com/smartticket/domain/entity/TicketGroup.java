package com.smartticket.domain.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单组实体，对应表 {@code ticket_group}。
 *
 * <p>工单组用于表达 P1 阶段的服务团队或业务处理组，是队列、SLA 和自动分派规则的基础配置。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGroup {
    /** 工单组主键。 */
    private Long id;
    /** 工单组名称。 */
    private String groupName;
    /** 工单组编码，全局唯一。 */
    private String groupCode;
    /** 组负责人用户 ID，可为空。 */
    private Long ownerUserId;
    /** 是否启用，1-启用，0-停用。 */
    private Integer enabled;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
