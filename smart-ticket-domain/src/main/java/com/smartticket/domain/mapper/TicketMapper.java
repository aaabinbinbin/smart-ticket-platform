package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Ticket mapper for table {@code ticket}.
 */
@Mapper
public interface TicketMapper {

    int insert(Ticket ticket);

    Ticket findById(@Param("id") Long id);

    Ticket findVisibleById(@Param("id") Long id, @Param("userId") Long userId);

    Ticket findByTicketNo(@Param("ticketNo") String ticketNo);

    List<Ticket> findByCreatorId(@Param("creatorId") Long creatorId);

    List<Ticket> findByAssigneeId(@Param("assigneeId") Long assigneeId);

    List<Ticket> findByStatus(@Param("status") TicketStatusEnum status);

    List<Ticket> pageAll(
            @Param("status") String status,
            @Param("category") String category,
            @Param("priority") String priority,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAll(
            @Param("status") String status,
            @Param("category") String category,
            @Param("priority") String priority
    );

    List<Ticket> pageVisible(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("category") String category,
            @Param("priority") String priority,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countVisible(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("category") String category,
            @Param("priority") String priority
    );

    int updateAssignee(
            @Param("id") Long id,
            @Param("assigneeId") Long assigneeId,
            @Param("expectedStatus") TicketStatusEnum expectedStatus
    );

    int updateAssigneeAndStatus(
            @Param("id") Long id,
            @Param("assigneeId") Long assigneeId,
            @Param("expectedStatus") TicketStatusEnum expectedStatus,
            @Param("status") TicketStatusEnum status
    );

    int updateStatus(
            @Param("id") Long id,
            @Param("expectedStatus") TicketStatusEnum expectedStatus,
            @Param("status") TicketStatusEnum status,
            @Param("solutionSummary") String solutionSummary
    );
}
