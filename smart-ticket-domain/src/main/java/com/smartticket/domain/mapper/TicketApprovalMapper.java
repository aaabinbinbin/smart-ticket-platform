package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketApproval;
import com.smartticket.domain.enums.TicketApprovalStatusEnum;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单审批映射接口定义。
 */
@Mapper
public interface TicketApprovalMapper {

    /**
     * 处理新增。
     */
    int insert(TicketApproval approval);

    /**
     * 查询按工单ID。
     */
    TicketApproval findByTicketId(@Param("ticketId") Long ticketId);

    /**
     * 按重新提交场景更新。
     */
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

    /**
     * 更新决策。
     */
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
