package com.smartticket.agent.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体工单领域记忆类。
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
}
