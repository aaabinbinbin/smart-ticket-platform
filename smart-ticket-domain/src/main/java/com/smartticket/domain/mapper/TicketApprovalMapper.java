package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketApprovalMapper {

    int insert(TicketApproval approval);

    TicketApproval findByTicketId(@Param("ticketId") Long ticketId);

    int updateForResubmit(
            @Param("ticketId") Long ticketId,
            @Param("templateId") Long templateId,
            @Param("currentStepOrder") Integer currentStepOrder,
            @Param("approvalStatus") TicketApprovalStatusEnum approvalStatus,
            @Param("approverId") Long approverId,
            @Param("requestedBy") Long requestedBy,
            @Param("submitComment") String submitComment,
            @Param("submittedAt") LocalDateTime submittedAt
    );

    int updateDecision(
            @Param("ticketId") Long ticketId,
            @Param("expectedStatus") TicketApprovalStatusEnum expectedStatus,
            @Param("approvalStatus") TicketApprovalStatusEnum approvalStatus,
            @Param("currentStepOrder") Integer currentStepOrder,
            @Param("approverId") Long approverId,
            @Param("decisionComment") String decisionComment,
            @Param("decidedAt") LocalDateTime decidedAt
    );
}
