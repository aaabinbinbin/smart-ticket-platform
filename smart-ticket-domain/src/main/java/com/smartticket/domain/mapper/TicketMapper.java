package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.Ticket;
import com.smartticket.domain.enums.TicketPriorityEnum;
import com.smartticket.domain.enums.TicketStatusEnum;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单映射接口定义。
 */
@Mapper
public interface TicketMapper {

    /**
     * 处理新增。
     */
    int insert(Ticket ticket);

    /**
     * 查询按ID。
     */
    Ticket findById(@Param("id") Long id);

    /**
     * 查询可见按ID。
     */
    Ticket findVisibleById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 查询按工单编号。
     */
    Ticket findByTicketNo(@Param("ticketNo") String ticketNo);

    /**
     * 查询按创建人ID。
     */
    List<Ticket> findByCreatorId(@Param("creatorId") Long creatorId);

    /**
     * 查询按处理人ID。
     */
    List<Ticket> findByAssigneeId(@Param("assigneeId") Long assigneeId);

    /**
     * 统计开放Assigned工单。
     */
    long countOpenAssigned工单(@Param("assigneeId") Long assigneeId);

    /**
     * 查询按状态。
     */
    List<Ticket> findByStatus(@Param("status") TicketStatusEnum status);

    /**
     * 分页查询全部。
     */
    List<Ticket> pageAll(@Param("status") String status, @Param("type") String type, @Param("category") String category, @Param("priority") String priority, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 统计全部。
     */
    long countAll(@Param("status") String status, @Param("type") String type, @Param("category") String category, @Param("priority") String priority);

    /**
     * 分页查询可见。
     */
    List<Ticket> pageVisible(@Param("userId") Long userId, @Param("status") String status, @Param("type") String type, @Param("category") String category, @Param("priority") String priority, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 统计可见。
     */
    long countVisible(@Param("userId") Long userId, @Param("status") String status, @Param("type") String type, @Param("category") String category, @Param("priority") String priority);

    /**
     * 更新处理人。
     */
    int updateAssignee(@Param("id") Long id, @Param("assigneeId") Long assigneeId, @Param("expectedStatus") TicketStatusEnum expectedStatus);

    /**
     * 更新处理人并状态。
     */
    int updateAssigneeAndStatus(@Param("id") Long id, @Param("assigneeId") Long assigneeId, @Param("expectedStatus") TicketStatusEnum expectedStatus, @Param("status") TicketStatusEnum status);

    /**
     * 更新队列绑定关系。
     */
    int updateQueueBinding(@Param("id") Long id, @Param("groupId") Long groupId, @Param("queueId") Long queueId);

    /**
     * 更新状态。
     */
    int updateStatus(@Param("id") Long id, @Param("expectedStatus") TicketStatusEnum expectedStatus, @Param("status") TicketStatusEnum status, @Param("solutionSummary") String solutionSummary);

    /**
     * 更新优先级。
     */
    int updatePriority(@Param("id") Long id, @Param("priority") TicketPriorityEnum priority);
}
