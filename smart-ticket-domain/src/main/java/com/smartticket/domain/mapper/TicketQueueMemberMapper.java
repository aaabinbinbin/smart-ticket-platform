package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketQueueMember;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketQueueMemberMapper {

    int insert(TicketQueueMember member);

    TicketQueueMember findById(@Param("id") Long id);

    TicketQueueMember findByQueueIdAndUserId(@Param("queueId") Long queueId, @Param("userId") Long userId);

    List<TicketQueueMember> findByQueueId(@Param("queueId") Long queueId, @Param("enabled") Integer enabled);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    int updateLastAssignedAt(@Param("id") Long id);
}
