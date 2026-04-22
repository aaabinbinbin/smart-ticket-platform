package com.smartticket.biz.repository.approval;

import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import com.smartticket.domain.mapper.TicketApprovalMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class TicketApprovalRepository {
    private final TicketApprovalMapper ticketApprovalMapper;

    public TicketApprovalRepository(TicketApprovalMapper ticketApprovalMapper) {
        this.ticketApprovalMapper = ticketApprovalMapper;
    }

    public int insert(TicketApproval approval) {
        return ticketApprovalMapper.insert(approval);
    }

    public TicketApproval findByTicketId(Long ticketId) {
        return ticketApprovalMapper.findByTicketId(ticketId);
    }

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

