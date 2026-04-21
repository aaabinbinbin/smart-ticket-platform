package com.smartticket.domain.mapper;

import com.smartticket.domain.entity.TicketQueue;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工单队列 Mapper，只负责数据访问，不承载业务权限和规则。
 */
@Mapper
public interface TicketQueueMapper {

    int insert(TicketQueue ticketQueue);

    TicketQueue findById(@Param("id") Long id);

    TicketQueue findByCode(@Param("queueCode") String queueCode);

    List<TicketQueue> findEnabledByGroupId(@Param("groupId") Long groupId);

    List<TicketQueue> page(
            @Param("groupId") Long groupId,
            @Param("keyword") String keyword,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(
            @Param("groupId") Long groupId,
            @Param("keyword") String keyword,
            @Param("enabled") Integer enabled
    );

    int update(TicketQueue ticketQueue);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
