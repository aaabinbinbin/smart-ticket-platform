package com.smartticket.biz.repository.approval;

import com.smartticket.domain.entity.TicketApprovalStep;
import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import com.smartticket.domain.mapper.TicketApprovalStepMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class TicketApprovalStepRepository {
    private final TicketApprovalStepMapper ticketApprovalStepMapper;

    public TicketApprovalStepRepository(TicketApprovalStepMapper ticketApprovalStepMapper) {
        this.ticketApprovalStepMapper = ticketApprovalStepMapper;
    }

    public int insertBatch(List<TicketApprovalStep> steps) {
        return ticketApprovalStepMapper.insertBatch(steps);
    }

    public int deleteByTicketId(Long ticketId) {
        return ticketApprovalStepMapper.deleteByTicketId(ticketId);
    }

    public List<TicketApprovalStep> findByTicketId(Long ticketId) {
        return ticketApprovalStepMapper.findByTicketId(ticketId);
    }

    public TicketApprovalStep findCurrentPendingByTicketId(Long ticketId) {
        return ticketApprovalStepMapper.findCurrentPendingByTicketId(ticketId);
    }

    public TicketApprovalStep findNextWaitingByTicketId(Long ticketId, Integer currentStepOrder) {
        return ticketApprovalStepMapper.findNextWaitingByTicketId(ticketId, currentStepOrder);
    }

    public int updateDecision(Long id, TicketApprovalStepStatusEnum expectedStatus, TicketApprovalStepStatusEnum stepStatus, String decisionComment, LocalDateTime decidedAt) {
        return ticketApprovalStepMapper.updateDecision(id, expectedStatus, stepStatus, decisionComment, decidedAt);
    }

    public int activateStep(Long id, TicketApprovalStepStatusEnum expectedStatus, TicketApprovalStepStatusEnum stepStatus) {
        return ticketApprovalStepMapper.activateStep(id, expectedStatus, stepStatus);
    }
}

