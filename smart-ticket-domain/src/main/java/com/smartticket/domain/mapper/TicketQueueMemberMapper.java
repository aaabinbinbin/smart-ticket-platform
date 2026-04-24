package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketQueueMember;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单队列成员映射接口定义。
 */
@Mapper
public interface TicketQueueMemberMapper {

    /**
     * 处理新增。
     */
    int insert(TicketQueueMember member);

    /**
     * 查询按ID。
     */
    TicketQueueMember findById(@Param("id") Long id);

    /**
     * 查询按队列ID并用户ID。
     */
    TicketQueueMember findByQueueIdAndUserId(@Param("queueId") Long queueId, @Param("userId") Long userId);

    /**
     * 查询按队列ID。
     */
    List<TicketQueueMember> findByQueueId(@Param("queueId") Long queueId, @Param("enabled") Integer enabled);

    /**
     * 更新启用。
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    /**
     * 更新最近分派时间。
     */
    int updateLastAssignedAt(@Param("id") Long id);
}
