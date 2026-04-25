package com.smartticket.agent.model;

import com.smartticket.domain.enums.MemorySource;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体工单领域记忆类。
 *
 * <p>记忆不是权威事实源，数据库/Tool 实时查询结果才是权威事实。
 * 记忆用于上下文缓存和偏好线索，不替代数据库查询。</p>
 *
 * <p>每条记忆包含来源(source)、置信度(confidence)和过期时间(expiresAt)，用于判断可信度。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTicketDomainMemory {
    // 工单ID
    private Long ticketId;
    // latestEvent摘要
    private String latestEventSummary;
    // risk状态
    private String riskStatus;
    // 审批状态
    private String approvalStatus;
    // 更新时间
    private LocalDateTime updatedAt;

    // ========== 可靠性元数据 ==========

    /** 记忆来源类型。 */
    private MemorySource source;

    /** 置信度（0-1），越低越只能用于推荐，不能自动执行。 */
    private Double confidence;

    /** 过期时间，过期后不应使用此记忆。 */
    private LocalDateTime expiresAt;
}
