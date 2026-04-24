package com.smartticket.biz.repository.approval;

import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import com.smartticket.domain.mapper.TicketApprovalMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/**
 * 工单审批仓储仓储接口。
 */
@Repository
public class TicketApprovalRepository {
    // 工单审批映射接口
    private final TicketApprovalMapper ticketApprovalMapper;

    /**
     * 构造工单审批仓储。
     */
    public TicketApprovalRepository(TicketApprovalMapper ticketApprovalMapper) {
        this.ticketApprovalMapper = ticketApprovalMapper;
    }

    /**
     * 处理新增。
     */
    public int insert(TicketApproval approval) {
        return ticketApprovalMapper.insert(approval);
    }

    /**
     * 查询按工单ID。
     */
    public TicketApproval findByTicketId(Long ticketId) {
        return ticketApprovalMapper.findByTicketId(ticketId);
    }

    /**
     * 按重新提交场景更新。
     */
    public int updateForResubmit(
            Long ticketId,
            Long templateId,
            Integer currentStepOrder,
            TicketApprovalStatusEnum approvalStatus,
            Long approverId,
            Long requestedBy,
            String submitComment,
            LocalDateTime submittedAt
    ) {
        return ticketApprovalMapper.updateForResubmit(
                ticketId,
                templateId,
                currentStepOrder,
                approvalStatus,
                approverId,
                requestedBy,
                submitComment,
                submittedAt
        );
    }

    /**
     * 更新决策。
     */
    public int updateDecision(
            Long ticketId,
            TicketApprovalStatusEnum expectedStatus,
            TicketApprovalStatusEnum approvalStatus,
            Integer currentStepOrder,
            Long approverId,
            String decisionComment,
            LocalDateTime decidedAt
    ) {
        return ticketApprovalMapper.updateDecision(
                ticketId,
                expectedStatus,
                approvalStatus,
                currentStepOrder,
                approverId,
                decisionComment,
                decidedAt
        );
    }
}

