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

    /**
     * 处理新增。
     */
    int insert(TicketQueue ticketQueue);

    /**
     * 查询按ID。
     */
    TicketQueue findById(@Param("id") Long id);

    /**
     * 查询按编码。
     */
    TicketQueue findByCode(@Param("queueCode") String queueCode);

    /**
     * 查询启用按分组ID。
     */
    List<TicketQueue> findEnabledByGroupId(@Param("groupId") Long groupId);

    /**
     * 分页查询。
     */
    List<TicketQueue> page(
            @Param("groupId") Long groupId,
            @Param("keyword") String keyword,
            @Param("enabled") Integer enabled,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 获取统计信息。
     */
    long count(
            @Param("groupId") Long groupId,
            @Param("keyword") String keyword,
            @Param("enabled") Integer enabled
    );

    /**
     * 更新。
     */
    int update(TicketQueue ticketQueue);

    /**
     * 更新启用。
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
