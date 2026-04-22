package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketApprovalStep;
import com.smartticket.domain.enums.TicketApprovalStepStatusEnum;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketApprovalStepMapper {

    int insertBatch(@Param("steps") List<TicketApprovalStep> steps);

    int deleteByTicketId(@Param("ticketId") Long ticketId);

    List<TicketApprovalStep> findByTicketId(@Param("ticketId") Long ticketId);

    TicketApprovalStep findCurrentPendingByTicketId(@Param("ticketId") Long ticketId);

    TicketApprovalStep findNextWaitingByTicketId(@Param("ticketId") Long ticketId, @Param("currentStepOrder") Integer currentStepOrder);

    int updateDecision(
            @Param("id") Long id,
            @Param("expectedStatus") TicketApprovalStepStatusEnum expectedStatus,
            @Param("stepStatus") TicketApprovalStepStatusEnum stepStatus,
            @Param("decisionComment") String decisionComment,
            @Param("decidedAt") LocalDateTime decidedAt
    );

    int activateStep(@Param("id") Long id, @Param("expectedStatus") TicketApprovalStepStatusEnum expectedStatus, @Param("stepStatus") TicketApprovalStepStatusEnum stepStatus);
}
