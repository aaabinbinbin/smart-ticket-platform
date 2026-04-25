package com.smartticket.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端 Dashboard 响应 DTO。
 *
 * <p>包含工单、RAG、Agent 三大板块的核心指标。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponseDTO {

    /** 工单指标。 */
    private TicketDashboardDTO ticket;

    /** RAG 指标。 */
    private RagDashboardDTO rag;

    /** Agent 指标。 */
    private AgentDashboardDTO agent;
}
