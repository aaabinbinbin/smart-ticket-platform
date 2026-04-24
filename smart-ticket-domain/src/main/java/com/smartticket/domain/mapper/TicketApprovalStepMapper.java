package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketApprovalStep;
import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单审批步骤映射接口定义。
 */
@Mapper
public interface TicketApprovalStepMapper {

    /**
     * 新增Batch。
     */
    int insertBatch(@Param("steps") List<TicketApprovalStep> steps);

    /**
     * 删除按工单ID。
     */
    int deleteByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 查询按工单ID。
     */
    List<TicketApprovalStep> findByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 查询当前Pending按工单ID。
     */
    TicketApprovalStep findCurrentPendingByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 查询NextWaiting按工单ID。
     */
    TicketApprovalStep findNextWaitingByTicketId(@Param("ticketId") Long ticketId, @Param("currentStepOrder") Integer currentStepOrder);

    /**
     * 更新决策。
     */
    int updateDecision(
            @Param("id") Long id,
            @Param("expectedStatus") TicketApprovalStepStatusEnum expectedStatus,
            @Param("stepStatus") TicketApprovalStepStatusEnum stepStatus,
            @Param("decisionComment") String decisionComment,
            @Param("decidedAt") LocalDateTime decidedAt
    );

    /**
     * 处理步骤。
     */
    int activateStep(@Param("id") Long id, @Param("expectedStatus") TicketApprovalStepStatusEnum expectedStatus, @Param("stepStatus") TicketApprovalStepStatusEnum stepStatus);
}
