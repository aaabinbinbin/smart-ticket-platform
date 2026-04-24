package com.smartticket.biz.repository.approval;

import com.smartticket.domain.entity.TicketApprovalStep;
import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import com.smartticket.domain.mapper.TicketApprovalStepMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 工单审批步骤仓储仓储接口。
 */
@Repository
public class TicketApprovalStepRepository {
    // 工单审批步骤映射接口
    private final TicketApprovalStepMapper ticketApprovalStepMapper;

    /**
     * 构造工单审批步骤仓储。
     */
    public TicketApprovalStepRepository(TicketApprovalStepMapper ticketApprovalStepMapper) {
        this.ticketApprovalStepMapper = ticketApprovalStepMapper;
    }

    /**
     * 新增Batch。
     */
    public int insertBatch(List<TicketApprovalStep> steps) {
        return ticketApprovalStepMapper.insertBatch(steps);
    }

    /**
     * 删除按工单ID。
     */
    public int deleteByTicketId(Long ticketId) {
        return ticketApprovalStepMapper.deleteByTicketId(ticketId);
    }

    /**
     * 查询按工单ID。
     */
    public List<TicketApprovalStep> findByTicketId(Long ticketId) {
        return ticketApprovalStepMapper.findByTicketId(ticketId);
    }

    /**
     * 查询当前Pending按工单ID。
     */
    public TicketApprovalStep findCurrentPendingByTicketId(Long ticketId) {
        return ticketApprovalStepMapper.findCurrentPendingByTicketId(ticketId);
    }

    /**
     * 查询NextWaiting按工单ID。
     */
    public TicketApprovalStep findNextWaitingByTicketId(Long ticketId, Integer currentStepOrder) {
        return ticketApprovalStepMapper.findNextWaitingByTicketId(ticketId, currentStepOrder);
    }

    /**
     * 更新决策。
     */
    public int updateDecision(Long id, TicketApprovalStepStatusEnum expectedStatus, TicketApprovalStepStatusEnum stepStatus, String decisionComment, LocalDateTime decidedAt) {
        return ticketApprovalStepMapper.updateDecision(id, expectedStatus, stepStatus, decisionComment, decidedAt);
    }

    /**
     * 处理步骤。
     */
    public int activateStep(Long id, TicketApprovalStepStatusEnum expectedStatus, TicketApprovalStepStatusEnum stepStatus) {
        return ticketApprovalStepMapper.activateStep(id, expectedStatus, stepStatus);
    }
}

